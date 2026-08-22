# CampusFix Architecture

## Architecture choice

Use a modular monolith.

This is intentional.

For a student project, a well-designed modular monolith is easier to:
- understand
- test
- deploy
- debug
- explain in interviews

Do not split into microservices merely to add technology.

## High-level architecture

Browser
↓
HTML/CSS/JavaScript
↓
HTTP/REST
↓
Spring Boot
↓
Controller
↓
Service
↓
Repository
↓
JPA/Hibernate
↓
MySQL

Redis will be added later for selected caching/rate-limiting use cases.

## Build and tooling

| Choice | Version | Reason |
|---|---|---|
| Spring Boot | 3.5.6 | Current stable line; requires Java 17+ |
| Java target | 21 | Documented target. The installed JDK is 22, so Maven compiles with `--release 21` and the bytecode still matches. |
| Maven Wrapper | 3.9.9 | `mvn` is not on PATH on the development machine. The wrapper is committed so the build works from a fresh clone. |
| Lombok | not used | Generated methods are harder to explain in an interview. Java `record` covers most DTO boilerplate. |

Dependencies are added in the phase that first needs them, not upfront.
Adding `spring-boot-starter-data-jpa` before a datasource exists would stop the
application from starting, which makes every later failure ambiguous.

## Backend package direction

com.campusfix
├── common
├── auth
├── user
├── department
├── category
├── location
├── request
├── assignment
├── comment
├── attachment
├── sla
├── escalation
├── feedback
├── notification
├── dashboard
├── audit
├── config
└── exception

Do not create packages before the relevant feature is actually implemented.

## Layer responsibilities

### Controller
- HTTP concerns
- request/response mapping
- validation trigger
- authentication context access
- no business-heavy logic

### Service
- business rules
- workflow transitions
- authorization checks where domain-specific
- transaction boundaries
- orchestration

### Repository
- persistence operations
- queries
- no business rules

### Entity
- database representation
- relationships
- persistence state

### DTO
- API contract
- input/output separation from database entities

### Exception
- predictable error model
- centralized exception handling

## Important principle

Do not expose JPA entities directly from every API.

Use DTOs where API boundaries need them.

## Request workflow example

POST /api/requests
↓
RequestController
↓
RequestService
↓
validate category
↓
determine department
↓
calculate SLA dueAt
↓
create request
↓
create activity event
↓
save transaction
↓
return response DTO

## Transaction example

A request assignment may update:
- request assignment
- request status
- activity log

These changes should be treated as one business operation.

Use @Transactional where appropriate.

## Security

Security is introduced after core CRUD is understood.

Final flow:

Browser
↓
JWT
↓
Spring Security filter chain
↓
authenticated user
↓
role/permission checks
↓
controller
