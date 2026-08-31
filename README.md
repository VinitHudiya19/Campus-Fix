# CampusFix

A college service request management platform. Students report campus problems —
broken fans, dead Wi-Fi, leaking taps — and follow them until they are fixed.
Technicians work on what they are assigned, department heads run their queue, and
admins manage the setup.

**Live demo:** https://campus-fix-2g18.onrender.com

The demo runs on Render's free plan, so the first request after a period of
inactivity takes about a minute while the instance wakes up.

---

## The problem

Right now these issues get reported over WhatsApp groups, phone calls, or by
walking into an office. That works until somebody asks a simple question:

- Who is actually fixing this?
- Was it reported already?
- How long has it been pending?
- Did anyone check it was really fixed?

Nothing is written down in a form you can search or count. CampusFix gives the
same process a structure: every report becomes a numbered request with an owner,
a deadline, a history, and a student who has to agree it is done before it closes.

---

## Screenshots

| | |
|---|---|
| ![Department head's request queue](screenshots/requests-list.png) | ![A single request with its history](screenshots/request-detail.png) |
| The department head's queue, with SLA state on every row | One request: description, photos, timeline, and the actions available to you |

---

## How a request moves

```text
Student creates a request
        ↓
      OPEN                    category decides the department
        ↓                     head assigns a technician
     ASSIGNED
        ↓                     technician starts work
   IN_PROGRESS
        ↓                     technician records what they did
     RESOLVED
        ↓                     student confirms
      CLOSED
```

Two other paths exist:

- **RESOLVED → REOPENED → IN_PROGRESS** — the student says it is still broken
- **OPEN / ASSIGNED / IN_PROGRESS / REOPENED → REJECTED** — staff reject a
  duplicate or out-of-scope request, with a reason

The seven statuses are `OPEN`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`,
`REOPENED`, `REJECTED`. Only five actions can move a request between them, and
each one records who did it and why.

---

## Roles

Four roles, defined in `Role.java` and enforced in `SecurityConfig`.

| Role | What they can do |
|---|---|
| **STUDENT** | Report problems, attach photos, follow their own requests, confirm or reopen a resolution |
| **TECHNICIAN** | See the work assigned to them, start it, record what they did, mark it resolved |
| **DEPARTMENT_HEAD** | See their department's whole queue, assign and reassign technicians, reject requests, view reports for their department |
| **ADMIN** | Manage departments, categories, locations, users and SLA targets; see everything |

A student never picks a department. They pick a category — "Wi-Fi", "Fan" — and
the category decides which department gets the work.

---

## Features

**Students**
- Create a request with a category, optional location and priority
- Attach photos (PNG, JPEG, GIF, WebP)
- Track status, deadline and full history
- Confirm a resolution, or reopen it with a reason

**Technicians**
- See only requests assigned to them
- Start work and mark resolved, with a note describing the fix

**Department heads**
- See the department queue, including unassigned requests
- Assign or reassign, with each technician's current workload shown
- Reject requests with a reason
- Reports for their own department

**Admins**
- Manage departments, categories, locations and users
- Configure SLA targets per priority
- Reports across all departments
- Trigger the overdue check manually

**Notifications**
- A bell in the navigation with an unread count
- Told when: work is assigned to you, your request is resolved or rejected, your
  fix is reopened or confirmed, or a request you are responsible for is escalated
- Optional email copy of the same notification, if a mail server is configured

**Across the system**
- Search over request number, title and description
- Filter by status, category, priority and unassigned
- Sorting and pagination
- An activity timeline on every request
- SLA tracking with automatic two-level escalation

---

## Tech stack

**Backend**
- Java 21
- Spring Boot 3.5.6
- Spring Web — REST controllers
- Spring Data JPA + Hibernate — persistence
- Spring Security — authentication and role-based authorization
- JJWT 0.12.6 — JWT creation and verification
- Spring Boot Actuator — `/actuator/health` for the hosting platform's health check
- Bean Validation — request validation

**Database**
- MySQL 8

**Frontend**
- HTML, CSS, JavaScript (no framework)
- Bootstrap 5 — layout and components, from a CDN
- Chart.js — the three charts on the reports screen, from a CDN

**Storage**
- Local disk by default
- AWS SDK for Java (S3) — used under the `s3` profile for S3-compatible storage

**Notifications**
- Spring's application events — services publish, the notification module listens,
  so nothing in `request` or `sla` knows notifications exist
- Spring Mail — used only by the email channel, which registers only when a mail
  host is configured

**Testing**
- JUnit 5, Mockito, AssertJ
- H2 — in-memory database so tests need no MySQL

**Deployment**
- Docker (multi-stage build)
- Docker Compose for local development

There is **no** Redis or message queue in this project. The frontend has no build
step — the files are served by Spring directly.

---

## Architecture

A single Spring Boot application (a monolith), organised by feature rather than
by layer.

```text
Browser (HTML/JS)
      ↓  fetch + JWT in the Authorization header
REST Controller      reads the request, returns a DTO, no business rules
      ↓
Service              enforces the rules, owns the transaction
      ↓
Repository           Spring Data JPA
      ↓
Hibernate
      ↓
MySQL
```

Everything about requests lives in `com.campusfix.request`, everything about
users in `com.campusfix.user`, and so on. A change to one feature touches one
folder instead of four.

---

## Project structure

```text
CAMPUS-FIX/
├── src/
│   ├── main/
│   │   ├── java/com/campusfix/
│   │   │   ├── auth/          login, JWT issuing, current user
│   │   │   ├── user/          accounts and roles
│   │   │   ├── department/    the teams that fix things
│   │   │   ├── category/      problem types, each pointing at a department
│   │   │   ├── location/      where on campus
│   │   │   ├── request/       requests, assignment, status workflow
│   │   │   ├── attachment/    photo upload and download
│   │   │   ├── activity/      the timeline written on every change
│   │   │   ├── sla/           SLA targets, breach detection, escalation
│   │   │   ├── report/        aggregation queries for the reports screen
│   │   │   ├── demo/          demo data seeder
│   │   │   └── common/        security, storage, error handling, config
│   │   └── resources/
│   │       ├── static/        the frontend (HTML, CSS, JS)
│   │       └── application*.properties
│   └── test/java/com/campusfix/
├── scripts/smoke-test.sh      end-to-end API check
├── screenshots/
├── Dockerfile
├── compose.yaml
└── pom.xml
```

---

## Database

Ten entities. Hibernate generates the schema from them (`ddl-auto=update`);
there are no migration files.

| Entity | Purpose |
|---|---|
| `User` | Students and staff, with a role and an optional department |
| `Department` | A team — IT Support, Electrical |
| `Category` | A problem type, belonging to one department |
| `Location` | Campus, building, floor, room |
| `ServiceRequest` | The request itself |
| `Assignment` | One period during which a technician held a request |
| `ActivityLog` | Append-only history of everything that happened |
| `Attachment` | A record of an uploaded photo (the file lives in storage) |
| `SlaConfig` | Target hours and warning threshold per priority |
| `Escalation` | A record that a late request was pushed up a level |
| `Notification` | One thing a particular person should know, with a read state |

The relationships that matter:

- A **category** belongs to one **department** — this is what routes a request
- A **request** has one student, one category, an optional location, and a
  currently assigned technician
- **Assignments** accumulate rather than overwrite, so reassignment keeps history
- **Activity logs** and **escalations** both belong to a request

---

## API

All endpoints are under `/api`. Everything except login and the health check
needs an `Authorization: Bearer <token>` header.

```text
Authentication
POST   /api/auth/login
GET    /api/auth/me
PUT    /api/auth/password

Service requests
GET    /api/requests              search, filters, sorting, pagination
POST   /api/requests              students only
GET    /api/requests/{id}
GET    /api/requests/priorities
GET    /api/requests/statuses

Workflow
POST   /api/requests/{id}/start
POST   /api/requests/{id}/resolve
POST   /api/requests/{id}/reject
POST   /api/requests/{id}/confirm
POST   /api/requests/{id}/reopen
GET    /api/requests/{id}/available-actions
GET    /api/requests/{id}/timeline
GET    /api/requests/{id}/escalations

Assignment
POST   /api/requests/{id}/assign
DELETE /api/requests/{id}/assignment
GET    /api/requests/{id}/assignments
GET    /api/requests/{id}/assignable-technicians

Attachments
GET    /api/requests/{id}/attachments
POST   /api/requests/{id}/attachments
GET    /api/requests/{id}/attachments/{attachmentId}
DELETE /api/requests/{id}/attachments/{attachmentId}

Notifications
GET    /api/notifications
GET    /api/notifications/unread-count
PUT    /api/notifications/{id}/read
PUT    /api/notifications/read-all

Admin
GET/POST/PUT/DELETE  /api/departments, /api/categories, /api/locations, /api/users
GET    /api/sla       PUT /api/sla/{priority}      POST /api/sla/check-now
GET    /api/reports   admin and department head only
```

There is no signup endpoint. A college issues accounts, so users are created by
an admin through `POST /api/users`.

There is no Swagger UI in this project.

---

## Security

- **Passwords** are hashed with BCrypt. Nothing stores or returns a plain password.
- **JWT authentication**, stateless — no server-side session. A filter reads the
  token on each request and builds the caller's identity from its claims.
- **Role-based authorization** in one place (`SecurityConfig`), so it is possible
  to read the whole policy at once.
- **Row-level visibility**: a student sees only their own requests, a technician
  only what is assigned to them, a head only their department. This is pushed
  into the JPA query rather than filtered afterwards.
- **404 instead of 403** when reading a request outside your scope, so the API
  does not confirm that an id exists to someone who should not know.
- **Upload validation** by the file's magic bytes, not by its name or the
  `Content-Type` header, both of which the uploader controls. Downloads are sent
  with `Content-Disposition: attachment` and `X-Content-Type-Options: nosniff`.
- **Error responses** use one shape everywhere, and never include a stack trace.

There is no rate limiting.

---

## SLA and escalation

Each priority has a target time, configurable by an admin:

| Priority | Default target |
|---|---|
| LOW | 72 hours |
| MEDIUM | 48 hours |
| HIGH | 24 hours |
| CRITICAL | 4 hours |

When a request is created, its deadline is calculated and **stored**. Changing a
target later only affects new requests — a promise already made to a student does
not move.

The SLA state (`on track`, `due soon`, `breached`, `met`, `missed`) is computed
on read, since it changes with the clock alone.

A scheduled job runs every 15 minutes and looks for requests past their deadline
that are still unresolved. The first breach escalates to the **department head**;
if it is still unresolved 24 hours later, it escalates to the **administration**.
Both are written to the request's timeline. A resolved request is never
escalated — it is waiting on the student, not the department.

---

## Frontend

Plain HTML, CSS and JavaScript with Bootstrap. No build step: the files sit in
`src/main/resources/static` and Spring serves them.

Pages:

| Page | Who |
|---|---|
| `login.html` | Everyone |
| `index.html` | Dashboard — the tiles differ by role |
| `requests.html` | Request list, with search and filters |
| `request-new.html` | Report a problem (students) |
| `request-detail.html` | One request: description, photos, timeline, actions |
| `reports.html` | Admin and department head |
| `departments.html`, `categories.html`, `locations.html`, `users.html`, `sla.html` | Admin |
| `password.html` | Change your own password |

The screens ask the server what is possible rather than deciding themselves —
which buttons appear on a request comes from
`GET /api/requests/{id}/available-actions`, so the UI and the server cannot
disagree.

---

## Running it locally

### With Docker

```bash
docker compose up --build
```

Starts MySQL and the application, waits for the database to be ready, and loads
demo data. Open <http://localhost:8080>. Nothing else needs installing.

### Without Docker

You need **JDK 21+** and **MySQL 8**. Maven is not required — use the wrapper.

Create the database:

```sql
CREATE DATABASE campusfix CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Set your credentials — these are read from the environment, with defaults that
suit a standard local install:

```bash
export DB_USERNAME=your_username
export DB_PASSWORD=your_password
```

Run it:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

The `demo` profile fills an empty database with four departments, twelve
categories, eleven locations, sixteen people and seventeen requests spread across
every status and SLA state. It only runs when there are no requests yet, so it
will not overwrite real data. Drop the profile to start empty — you still get an
admin account, printed in the startup log.

### Demo accounts

Every seeded account uses the password `demo1234`.

| Email | Role |
|---|---|
| `priya.nair@college.edu` | Student |
| `amit.sharma@college.edu` | Technician, IT Support |
| `neha.rao@college.edu` | Department head, IT Support |
| `admin@campusfix.local` | Administrator (password `admin12345` locally) |

### Configuration

Everything is set through environment variables, with local defaults in
`application.properties`:

```text
DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD, DB_SSL_MODE
JWT_SECRET, JWT_EXPIRY_MINUTES
ADMIN_EMAIL, ADMIN_PASSWORD
STORAGE_LOCATION                      local disk path
S3_BUCKET, S3_ENDPOINT, S3_REGION     used only under the s3 profile
PORT                                  set by the hosting platform

SPRING_MAIL_HOST                      set this to turn email on; leave unset and
SPRING_MAIL_PORT                        only in-app notifications are used
SPRING_MAIL_USERNAME
SPRING_MAIL_PASSWORD
MAIL_FROM                             the From address on notification emails
BASE_URL                              used to build the link back to a request
```

### Turning email on

In-app notifications always work. Email is a second channel that only exists when
a mail host is configured — with `SPRING_MAIL_HOST` unset, the mail sender is
never created and the application behaves exactly as it does now.

For Gmail you need an **App Password**, not your normal password: turn on
2-Step Verification, then Google Account → Security → App passwords.

```bash
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=you@gmail.com
SPRING_MAIL_PASSWORD=<16-character app password>
MAIL_FROM=you@gmail.com
BASE_URL=https://your-app.onrender.com
```

Gmail allows roughly 500 messages a day and increasingly blocks app-password SMTP
from cloud IP ranges. For a demo, **Mailtrap** is the better choice — it captures
mail in a web inbox instead of delivering it, so nothing reaches real people:

```bash
SPRING_MAIL_HOST=sandbox.smtp.mailtrap.io
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<mailtrap username>
SPRING_MAIL_PASSWORD=<mailtrap password>
```

Profiles: `demo` (seed data), `prod` (server settings), `s3` (object storage).

### Tests

```bash
./mvnw test
```

52 tests on the service layer with mocked repositories. They run in a few seconds
and need no MySQL — the test profile uses in-memory H2.

There is also an end-to-end script that drives the real HTTP API as each role
against a running instance:

```bash
./scripts/smoke-test.sh
```

150 checks covering login, every role boundary, the full request lifecycle,
uploads, SLA escalation and reports. It writes real data, so point it at a
scratch database rather than one you are about to demonstrate.

---

## Typical demo flow

1. Sign in as `priya.nair@college.edu` (student) and report a problem
2. Notice the form shows which team it will go to once you pick a category
3. Sign in as `neha.rao@college.edu` (department head) — the request is in her
   queue, unassigned
4. Assign it to a technician; the dropdown shows each one's current workload
5. Sign in as `amit.sharma@college.edu` (technician) — only his own work is visible
6. Start it, then mark it resolved with a note
7. Back as the student: confirm the fix, or reopen it if it is not right
8. As the head, open **Reports** to see SLA compliance per department

Signing in as each role in turn is the quickest way to see the point of the
project, because the same request looks different to each of them.

---

## What I built

The parts I would want to talk through in an interview:

- **A status workflow as a table, not scattered if-statements.** Every legal move
  is one row of an enum: which statuses it applies from, what it changes the
  request to, who may do it, and whether a note is required. A request cannot go
  from `OPEN` to `CLOSED` because no action does that.
- **Row-level visibility pushed into the SQL.** A role becomes filter values in
  the WHERE clause, so the database never returns rows the caller should not see.
  Search was added the same way, so searching cannot be used to see another
  student's requests.
- **Only the student who reported a problem can confirm it is fixed** — not the
  technician, not an admin. If staff could close their own work, the resolution
  rate would be a number they award themselves.
- **Storing the SLA deadline but computing the SLA state.** The deadline is a
  promise made at a moment in time; the state changes with the clock alone.
- **Upload validation on magic bytes.** A file named `photo.jpg` and sent as
  `image/jpeg` can still be a PHP script — both of those come from the uploader.
- **A `FileStorage` interface with two implementations**, so switching from local
  disk to S3 is configuration rather than a rewrite.
- **Notifications sent after the transaction commits, on a separate thread.**
  Services publish an event and know nothing about notifications.
  `@TransactionalEventListener(AFTER_COMMIT)` means nobody is told about a change
  that later rolled back — an email cannot be un-sent — and `@Async` keeps a slow
  mail server out of the API response. The trade-off is real: by then there is
  nothing left to roll back, so a failed notification is lost rather than retried,
  and is logged rather than silent.
- **Three bugs that feature taught me**, all of which only appear when you run it:
  a self-invoked method skips Spring's proxy so its events are silently dropped;
  `@Async` with no configured executor creates a new thread per call forever; and
  a mail health check will report the whole application DOWN, which a hosting
  platform reads as a failed deploy.
- **Avoiding N+1 queries** with explicit `join fetch` on the list screens.
- Writing an end-to-end script that tests the real HTTP API, after finding that
  mocked unit tests could not tell me whether the security rules actually worked.

---

## Future improvements

Things that are not built:

- **Comments on requests.** Only the fixed workflow notes exist, so a student
  cannot ask "any update?".
- **Database migrations.** Hibernate generates the schema. Flyway with
  `ddl-auto=validate` is the correct answer before this held data that mattered.
- **Controller and integration tests.** The endpoint and security rules are
  covered by the smoke test script rather than by JUnit.
- **API documentation.** Swagger/OpenAPI would be worth adding.
- **Token revocation.** A JWT stays valid until it expires, so deactivating a
  user does not end their current session.
- **Search is a table scan.** `LIKE '%text%'` cannot use an index. Fine at a
  college's volume; a `FULLTEXT` index is the answer if it stops being.
- **Better mobile layout.** It works on a phone but was designed on a laptop.

---

## Notes on the live demo

It runs on Render's free plan with MySQL hosted on Aiven, so two things are worth
knowing:

- The instance sleeps after a period of inactivity, and the next request takes
  about a minute while it starts.
- The SLA check does not run while it is asleep, so escalation catches up on the
  next run rather than happening exactly on time.

No license file is included in this repository.
