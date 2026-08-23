# CampusFix Database Design

Database: MySQL

## Design principle

Prefer normalized relational data with clear foreign keys and constraints.

Do not create one giant table.

## What exists today

| Table | Status |
|---|---|
| `departments` | Implemented — Phase 3 |
| `categories` | Implemented — Phase 3 |
| everything below | Planned |

Tables are created by Hibernate from the entity classes
(`spring.jpa.hibernate.ddl-auto=update`). Flyway migrations replace this before
deployment; the reason is recorded in
[phases/phase-02-database.md](phases/phase-02-database.md).

Actual schema so far:

    departments(id, name UNIQUE, description, active, created_at, updated_at)

    categories(id, name, description, department_id FK -> departments(id),
               active, created_at, updated_at,
               UNIQUE KEY uk_category_name_department (name, department_id))

Category names are unique per department rather than globally, so "Wiring" can
exist under both Electrical and Hostel Maintenance.

## Initial entities

### users — built in Phase 4

```sql
users(id, full_name, email UNIQUE, password_hash, role, department_id FK NULL,
      active, created_at, updated_at)
```

`role` is a `VARCHAR(30)` holding the enum name: `STUDENT`, `TECHNICIAN`,
`DEPARTMENT_HEAD` or `ADMIN`.

`department_id` is null for students and admins, and required for technicians and
department heads. The database cannot express "required for two roles, forbidden
for the other two", so that rule is enforced in `UserService`.

### ~~roles~~ — dropped, see Phase 4

The original plan had a `roles` lookup table with `users.role_id` pointing at it.
That was dropped. A lookup table earns its place when rows can be added at
runtime, and roles cannot: the application code decides what each role may do, so
a fifth row inserted by hand would have no permissions attached to it anywhere.

Storing the name directly also removes a join from every user lookup and keeps
`SELECT role FROM users` readable.

Reasoning in full: [phases/phase-04-users-and-roles.md](phases/phase-04-users-and-roles.md).

### departments
- id
- name
- description
- active

### categories
- id
- name
- description
- department_id
- active

### locations — built in Phase 6

```sql
locations(id, campus, building, floor, room, active, created_at, updated_at,
          UNIQUE KEY uk_location_place (campus, building, floor, room))
```

Stored in parts rather than as one free-text line, so requests can be grouped by
building or campus for reporting. `floor` and `room` are nullable for places that
have neither, and the service normalises blank to null so the same room cannot be
added twice.

### service_requests — built in Phase 6

```sql
service_requests(id, request_number UNIQUE, title, description,
                 student_id FK, category_id FK, location_id FK NULL,
                 priority, status, due_at, created_at, updated_at)
```

`request_number` is `CF-<year>-<6-digit id>`, derived from the id so it cannot
collide.

`priority` and `status` are enum names stored as strings, for the same reason as
`users.role`.

`due_at` is calculated once at creation from the priority and then stored. If it
were recalculated on read, changing the SLA policy would retrospectively rewrite
whether old requests met their target.

`assigned_at`, `resolved_at` and `closed_at` are **not** here yet. They are added
by the phases that can actually set them — Phase 7 and Phase 8 — rather than
sitting empty in the meantime.

### assignments — built in Phase 7

```sql
assignments(id, request_id FK, technician_id FK, assigned_by FK, note,
            assigned_at, unassigned_at NULL)
```

One row per period of responsibility. Reassigning ends the running row and opens
a new one, so "who had this last week?" stays answerable.

The `active` flag originally planned here was dropped: it is exactly
`unassigned_at is null`, and storing one fact twice creates a way for the two to
disagree.

`service_requests` gained `assigned_technician_id` and `assigned_at` in the same
phase. That column duplicates the newest open row above, deliberately — every
list screen asks "who has this?", and deriving it would mean a subquery in the
busiest query in the application. Only `AssignmentService` writes either, inside
one transaction, so they cannot drift.

### comments
- id
- request_id
- user_id
- comment
- created_at

### attachments
- id
- request_id
- uploaded_by
- original_filename
- stored_filename/path
- content_type
- file_size
- created_at

### activity_logs
- id
- request_id
- actor_id
- event_type
- old_value nullable
- new_value nullable
- metadata nullable
- created_at

### sla_configs
- id
- priority
- duration_hours
- warning_percentage
- active

### escalations
- id
- request_id
- from_user_id
- to_user_id/department_id
- reason
- created_at
- resolved_at nullable

### feedback
- id
- request_id
- student_id
- rating
- comment
- created_at

## Relationships

users → service_requests
One student can create many requests.

departments → users
One department can contain many staff users.

departments → categories
A category belongs to one responsible department in the initial model.

categories → service_requests
One category can have many requests.

service_requests → assignments
A request can have multiple historical assignments, but only one active assignment at a time.

service_requests → comments
One request can have many comments.

service_requests → activity_logs
One request can have many events.

service_requests → feedback
At most one final feedback record per request.

## Indexing strategy

Do not add indexes everywhere.

Likely useful indexes:
- users.email unique
- service_requests.request_number unique
- service_requests.status
- service_requests.category_id
- service_requests.student_id
- service_requests.due_at
- assignments.technician_id
- activity_logs.request_id

Composite indexes will be considered after actual query patterns are known.

## Data integrity

Use:
- NOT NULL where appropriate
- foreign keys
- unique constraints
- sensible varchar lengths
- enum/string strategy chosen deliberately
- timestamps
- transaction boundaries

Never rely only on frontend validation.
