# CampusFix — College Service Request Management Platform

CampusFix is a student-developed, production-oriented college service request management platform.

The goal is NOT to build a huge "AI-looking" demo. The goal is to build a believable, maintainable application that a student developer can understand completely and explain in a placement interview.

## Product definition

CampusFix lets students report campus issues and lets the responsible staff manage those requests from creation through resolution and student confirmation.

Example:

Student → Create Request → Department → Technician → In Progress → Resolved → Student Confirms → Closed

## Primary users

1. STUDENT
2. TECHNICIAN
3. DEPARTMENT_HEAD
4. ADMIN

## Technology

### Backend
- Java 21
- Spring Boot
- Maven
- Spring Web
- Spring Data JPA
- Hibernate
- MySQL
- Bean Validation
- Spring Security + JWT (later phase)

### Frontend
- HTML
- CSS
- Vanilla JavaScript
- Bootstrap only where useful
- No React
- No Tailwind
- No generated UI framework

### Engineering
- JUnit 5
- Mockito
- Swagger/OpenAPI
- Git/GitHub
- Redis (later)
- Docker + Docker Compose (later)
- CI/CD + cloud deployment (final phase)

## UI direction

The interface must look like a real student-built college product:
- Light theme
- Clean white/soft-gray surfaces
- Restrained blue accent
- Good spacing
- Clear typography
- Simple cards and tables
- Human-readable labels
- No excessive gradients
- No glassmorphism
- No giant hero sections inside dashboards
- No excessive animations
- No AI-generated-looking decorative UI
- Responsive enough for laptop/tablet
- Accessibility and readable contrast matter

Do not use fake statistics just to make dashboards look impressive. Seed/demo data must be clearly realistic.

## Development philosophy

Build a modular monolith first.

Do NOT introduce:
- microservices
- Kafka
- Kubernetes
- AI/ML
- complicated event-driven architecture

unless a later phase explicitly requires them.

Every feature must have:
1. A product reason
2. A business rule
3. A database model if needed
4. An API
5. Validation
6. Error handling
7. Tests where appropriate
8. Frontend integration
9. Documentation

## Documentation rule

Documentation is a first-class part of the repository.

For every phase:
- update the relevant document in `/docs`
- document important decisions
- document API behavior
- document database changes
- document setup/debugging discoveries
- do not write documentation after the entire project is finished

The project should always be understandable by opening `/docs`.

## Running locally

Needs a JDK 21 or newer and MySQL 8. Maven is **not** required — the project
ships a wrapper.

1. Create the database:

       CREATE DATABASE campusfix CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

2. If your MySQL root password is not `root`, set it in the environment. Do not
   edit the properties file:

       export DB_PASSWORD=yourpassword

3. Start the application:

       ./mvnw spring-boot:run

   It runs on http://localhost:8080 and creates its tables on first start.

4. Check it is alive:

       curl http://localhost:8080/api/hello

Run the tests — these use an in-memory database and need no MySQL:

    ./mvnw test

On Windows CMD or PowerShell use `mvnw.cmd` instead of `./mvnw`.
If the wrapper cannot find a JDK, set `JAVA_HOME` first.

## Understanding the code

`docs/phases/` explains every phase in plain language: what was built, why it was
built that way, and how it works. Start at
[docs/phases/README.md](docs/phases/README.md).

## Phase roadmap

See:
- docs/00-product-spec.md
- docs/01-architecture.md
- docs/02-database-design.md
- docs/03-api-contract.md
- docs/04-development-phases.md
- docs/05-ui-design.md
- docs/06-coding-standards.md
- docs/07-deployment.md
- docs/08-interview-preparation.md
- docs/09-progress-log.md

## Definition of done

A phase is NOT complete because the code compiles.

A phase is complete only when:
- feature works
- edge cases are handled
- code is understandable
- tests exist where appropriate
- UI is usable
- documentation is updated
- Git commit is made
