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

### users
- id
- name
- email
- password_hash
- role_id
- department_id nullable
- active
- created_at
- updated_at

### roles
- id
- name

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

### locations
- id
- campus
- building
- floor
- room
- active

### service_requests
- id
- request_number
- student_id
- category_id
- location_id nullable
- title
- description
- priority
- status
- assigned_at nullable
- resolved_at nullable
- closed_at nullable
- due_at
- created_at
- updated_at

### assignments
- id
- request_id
- technician_id
- assigned_by
- assigned_at
- unassigned_at nullable
- active

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
