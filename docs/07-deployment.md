# CampusFix Deployment Plan

Deployment is a final phase, not the first phase.

## Local

Developer machine:
- Spring Boot
- MySQL
- Redis later

## Containerized

Docker Compose:
- campusfix-backend
- mysql
- redis

Frontend can initially be served as static files through the backend or a lightweight web server.

## Configuration

Never hardcode production credentials.

Use environment variables:

DB_HOST
DB_PORT
DB_NAME
DB_USERNAME
DB_PASSWORD
JWT_SECRET
REDIS_HOST
REDIS_PORT
MAIL_USERNAME
MAIL_PASSWORD

## Production checklist

- production database
- secure credentials
- HTTPS
- CORS configuration
- JWT secret
- file upload restrictions
- database migrations strategy
- logging
- health endpoint
- backup strategy
- error handling
- no debug mode
- no secrets in Git

## CI/CD

Pipeline:

GitHub push
↓
Build
↓
Unit tests
↓
Package
↓
Docker build
↓
Deploy

Do not implement CI/CD until the application works locally and in Docker.

## Deployment target

Choose the cloud provider only after Dockerization is stable.

Possible student-friendly choices can be evaluated later based on current pricing/free tiers and reliability.

Do not hardcode a cloud vendor into the application architecture.
