# CampusFix Interview Preparation

The goal is to understand the project deeply.

## Questions we should be able to answer

### Java
- Why Java?
- What is OOP?
- interface vs abstract class
- exception handling
- collections
- enum
- equals/hashCode

### Spring Boot
- What is dependency injection?
- What is IoC?
- @RestController
- @Service
- @Repository
- why constructor injection?
- how does a request reach the service?
- what is Spring Boot auto-configuration?

### JPA/Hibernate
- entity
- primary key
- relationships
- lazy vs eager loading
- N+1 problem
- transaction
- dirty checking

### MySQL
- normalization
- primary/foreign key
- index
- joins
- transactions
- ACID
- why pagination should happen at database/query level

### Security
- authentication vs authorization
- password hashing
- JWT
- role-based authorization
- why frontend-only authorization is insufficient

### CampusFix business logic
- why status transitions are restricted
- how SLA dueAt is calculated
- how escalation works
- how reassignment is tracked
- why activity logs exist
- why student confirmation is separate from technician resolution

### Redis
- why cache?
- cache invalidation
- what happens when cached data is stale?
- why not cache every request?

### Deployment
- Docker image vs container
- environment variables
- reverse proxy
- HTTPS
- CI/CD
- health checks

## Rule

If a feature cannot be explained clearly, it should not be added merely for the resume.
