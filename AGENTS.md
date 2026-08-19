# Project Guidelines

## Project

Energy Storage News Intelligence Platform.

## Backend stack

- Java 21
- Spring Boot
- Maven
- PostgreSQL
- Spring Data JPA

## Architecture

Use a modular monolith.

Prefer package-by-feature.

Do not introduce microservices unless explicitly requested.

## Development rules

- Keep implementations simple.
- Do not add dependencies unless necessary.
- Explain major architectural changes before implementing them.
- Use DTOs for REST APIs.
- Do not expose JPA entities directly.
- Write tests for new business logic.
- Run relevant tests after every change.
- Do not silently change existing APIs.

## Current scope

Only build what is explicitly requested.

Do not prematurely add:
- Redis
- Kafka
- Elasticsearch
- Kubernetes
- authentication
- AI
- frontend
- complex abstractions

## Verification

Before completing a task:
1. Compile the project.
2. Run relevant tests.
3. Report any failures.
4. Summarize changed files