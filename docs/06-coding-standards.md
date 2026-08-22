# CampusFix Coding Standards

## General

Write code as a student developer who cares about maintainability.

Do not over-engineer.

Prefer clear code over clever code.

## Java

- meaningful class names
- meaningful method names
- small methods
- avoid giant controllers
- business logic belongs in services
- repositories handle persistence
- DTOs define API boundaries
- use enums for fixed domain states where appropriate
- use LocalDateTime/Instant deliberately and consistently
- never hardcode passwords/secrets
- never log passwords or JWTs

## Comments

Do NOT comment every line.

Bad:
`// increment i`
`i++;`

Good:
Explain why something non-obvious exists.

Example:
`// Preserve the original SLA due time so a later priority change does not silently extend an already active request.`

## Validation

Validate at the API boundary and enforce business rules in the service layer.

Example:
- title cannot be blank
- description has sensible max length
- rating must be 1–5
- request cannot be reopened by an unauthorized user

## Exceptions

Use meaningful domain exceptions.

Examples:
- ResourceNotFoundException
- InvalidStatusTransitionException
- UnauthorizedRequestAccessException
- DuplicateFeedbackException

Centralize API error formatting.

## Database

Do not use `SELECT *` in custom queries unless there is a good reason.

Avoid N+1 query problems.

Use pagination for potentially large collections.

Add indexes based on access patterns.

## Git

Commit by logical feature.

Examples:
- feat: add category entity and repository
- feat: add category CRUD API
- fix: prevent invalid request status transition
- test: add request workflow tests
- docs: update database design

Do not commit:
- secrets
- `.env`
- build output
- IDE-specific junk
- generated credentials

## Configuration

Use environment variables for:
- database password
- JWT secret
- email credentials
- cloud credentials

Keep safe development defaults where appropriate.

## Documentation

Every completed phase must update:
- progress log
- relevant architecture/API/database docs
- known issues if any
