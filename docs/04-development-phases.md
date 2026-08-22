# CampusFix Development Phases

## Phase 0 — Product design
- product definition
- roles
- workflows
- business rules
- database plan
- API plan
- UI direction

DONE when docs are internally consistent.

## Phase 1 — Project setup
- Java 21
- Maven
- Spring Boot
- dependencies
- package structure
- application starts
- /api/hello works

## Phase 2 — MySQL connection
- create campusfix database
- configure application properties
- connect JPA
- verify connection
- understand generated SQL

## Phase 3 — Category + Department
- entities
- DTOs
- repositories
- services
- controllers
- validation
- basic frontend

## Phase 4 — User model
- roles
- users
- password handling
- basic user APIs

## Phase 5 — Authentication
- Spring Security
- password hashing
- JWT
- role-based authorization

## Phase 6 — Service Requests
- create
- list
- details
- category
- location
- request number
- status

## Phase 7 — Assignment
- technician assignment
- reassignment
- authorization
- assignment history

## Phase 8 — Workflow
- status transition rules
- resolution notes
- reopen
- confirmation
- activity logs

## Phase 9 — SLA
- SLA configuration
- dueAt
- warning state
- breach detection
- escalation

For the first implementation, SLA checks may be triggered by a scheduled job. Do not build a complex distributed scheduler.

## Phase 10 — Frontend
- login
- student dashboard
- request form
- request detail/timeline
- technician dashboard
- department head dashboard
- admin dashboard

## Phase 11 — Search/pagination/filtering
- server-side pagination
- sorting
- filtering
- search
- empty states
- loading/error states

## Phase 12 — Attachments and notifications
- secure upload validation
- local development storage
- notification model
- email later

## Phase 13 — Redis
Only after measuring/identifying useful use cases.

Initial candidates:
- dashboard cache
- frequently accessed read data
- rate limiting if justified

Never cache mutable request data carelessly.

## Phase 14 — Testing
- service unit tests
- controller tests
- repository/integration tests where useful
- workflow tests
- security tests

## Phase 15 — Docker
- backend image
- frontend serving strategy
- MySQL
- Redis
- environment variables
- health checks

## Phase 16 — CI/CD
- build
- test
- package
- Docker image
- deployment pipeline

## Phase 17 — Deployment
- cloud environment
- production database
- secrets
- HTTPS
- logs
- backups strategy
- monitoring basics

## Phase 18 — Final polish
- README
- architecture diagram
- API documentation
- screenshots
- demo data
- resume bullets
- interview questions
- known limitations
