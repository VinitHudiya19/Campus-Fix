# CampusFix Progress Log

Short running record of what is finished and what is next.
The full explanation of each phase lives in [docs/phases](phases/README.md).

## Status

| Phase | Topic | Status | Notes |
|---|---|---|---|
| 0 | Product design | Done | Roles, lifecycle, SLA and database plan written |
| 1 | Project setup | Done | [phase-01](phases/phase-01-project-setup.md) |
| 2 | Database connection | Done | [phase-02](phases/phase-02-database.md) |
| 3 | Departments and categories | Done | [phase-03](phases/phase-03-departments-and-categories.md) |
| 4 | Users and roles | Done | [phase-04](phases/phase-04-users-and-roles.md) |
| 5 | Login and security | Next | |
| 6–18 | See roadmap | Not started | [04-development-phases.md](04-development-phases.md) |

## 2026-08-22

### Phase 1 — Project setup

Spring Boot 3.5.6 project created and running on port 8080.

Environment on this machine: JDK 22.0.1, MySQL 8.0.41 running, and **no Maven on
PATH** — so the Maven Wrapper was added and committed. Build with `./mvnw`, not
`mvn`.

Compiling for Java 21 on a JDK 22 toolchain. In IntelliJ: Project SDK 22,
language level 21. That settles the SDK/Maven mismatch noted during Phase 0.

### Phase 2 — Database connection

Connected to MySQL through JPA. Credentials read from `DB_USERNAME` /
`DB_PASSWORD` environment variables with local-development fallbacks, so nothing
secret is committed.

Tests run against in-memory H2 with the `test` profile, activated for every test
run from `pom.xml`. The suite therefore needs no running MySQL.

### Phase 3 — Departments and categories

First real feature, plus the shared error handling that every later feature uses.

12 endpoints, 14 service tests, 15 tests total, all passing.

Testing policy for the rest of the project: test the business rules in the
service layer and nothing else. No test class for controllers that only delegate,
for DTOs, or for repository methods Spring Data generates. `contextLoads` stays
as the single check that the application still wires together.

### Phase 4 — Users and roles

Four roles, BCrypt password hashing, 8 endpoints. 20 tests passing.

Deviated from `02-database-design.md`: no `roles` table, the role is an enum
column on `users`. Roles cannot be added at runtime because the code decides what
each one may do, so a lookup table would only pretend to be configurable. Full
reasoning in the phase doc.

Only `spring-security-crypto` was added, not `spring-boot-starter-security`. The
full starter turns on the filter chain and locks every endpoint before a login
endpoint exists.

## Problems hit and how they were fixed

Recorded because each one costs an hour if you meet it cold.

**Test config silently replaced the main config.**
`src/test/resources/application.properties` does not merge with the main file —
it replaces it. `spring.application.name` disappeared and `/api/hello` started
returning the literal string `${spring.application.name}`.
Fix: renamed to `application-test.properties` and activated the `test` profile
from the Surefire plugin.

**Stale files in `target/`.**
After deleting the file above, the old copy was still in `target/test-classes`
and the same failure kept appearing. `./mvnw clean test` fixed it. When a change
appears to have no effect, clean first.

**Two different error formats.**
A plain `@RestControllerAdvice` did not catch validation errors — Spring's own
advice answered first, returning `{"type":"about:blank","title":"Bad Request"...}`
while our own errors used a different shape.
Fix: `GlobalExceptionHandler` now extends `ResponseEntityExceptionHandler` and
overrides `handleExceptionInternal`, so framework errors and domain errors come
back identical.

**MySQL `DATETIME` stores no timezone.**
Added `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` so timestamps do not
shift with the JVM's timezone. This matters for SLA arithmetic in Phase 9.

## Known limitations

| Limitation | Planned answer |
|---|---|
| `ddl-auto=update` manages the schema | Flyway migrations before deployment |
| No authentication on any endpoint | Phase 5 |
| Anyone can call `POST /api/users` and create an admin | Phase 5 — do not expose this build outside localhost |
| Tests use H2, production is MySQL | Testcontainers alongside the Docker phase, if dialect differences ever bite |
| `/api/hello` is scaffolding | Delete once Actuator is added |

## Next — Phase 5: Login and security

- `POST /api/auth/login` — email and password in, JWT out
- JWT validated on every request by a filter, so the API stays stateless
- endpoints locked down by role: only ADMIN manages users, departments and categories
- `GET /api/auth/me` so the frontend knows who is signed in
- a first admin account seeded on startup, since nobody can log in to create one

Phase 5 replaces `spring-security-crypto` with the full
`spring-boot-starter-security`.
