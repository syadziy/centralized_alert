# AGENTS.md
## Project Overview
Backend REST API menggunakan Java 21 dan Spring Boot 3.
Stack:
- Java 21
- Spring Boot 4.1.0
- Maven
- Spring Data JPA
- PostgreSQL
- JUnit 5
- Testcontainers
Tujuan utama:
- Maintainable
- Testable
- Clean Architecture
---
# Project Structure
src/main/java
   config/
      properties/
   controller/
   entities/
      constant/
      dto/
      mapper/
      model/
   job/
   repository/
      impl/
   service/
      impl/
   util/
      exception/
      handler/
src/test/java
---
# Development Commands
## Build
```bash
./mvn clean install
```
## Run
```bash
./mvn spring-boot:run
```
## Test
```bash
./mvn test
```
## Run specific test
```bash
./mvn -Dtest=UserServiceTest test
```
---
# Coding Guidelines
## Java Version
Always use Java 21 features where appropriate.
Preferred:
- Records
- Switch expressions
- Text blocks
- Optional
- Stream API
Avoid:
- Legacy Date API
- Raw types
- Anonymous classes unless required
---
## Naming Convention
Class:
- PascalCase
Method:
- camelCase
Constant:
- UPPER_SNAKE_CASE
Package:
- lowercase only
DTO suffix:
- Request
- Response
Exception suffix:
- Exception
---
# Spring Guidelines
Controllers should:
- Only validate request
- Call service
- Return DTO
Never place business logic in controller.
Services contain business logic.
Repositories only access database.
Never inject Repository into Controller.
---
# Entity Rules
Entity should not expose business logic unrelated to persistence.
Never return Entity directly from API.
Always convert:
Entity
↓
DTO
using Mapper.
---
# Mapper
Preferred:
MapStruct
Do not manually duplicate mapping code unless mapping is trivial.
---
# Database
Use:
Spring Data JPA
Avoid:
Native Query
unless performance requires it.
Every schema change must include:
- Flyway migration
---
# Error Handling
Use:
@RestControllerAdvice
Never catch Exception broadly.
Create specific exceptions.
Example:
UserNotFoundException
ProductAlreadyExistsException
---
# Logging
Use:
SLF4J
Example:
StructuredLog.*
log.info()
log.warn()
log.error()
Never use:
System.out.println()
---
# Validation
Use Bean Validation.
Example:
@NotNull
@NotBlank
@Email
@Valid
Validation belongs in Controller layer.
---
# Testing
Every new feature must include:
- Unit Test
Integration Test if repository or API changes.
Preferred libraries:
- JUnit 5
- Mockito
- Testcontainers
Coverage target:
80%+
---
# API Design
REST conventions:
GET /users
GET /users/{id}
POST /users
PUT /users/{id}
DELETE /users/{id}
Return proper HTTP status codes.
Avoid custom response wrappers unless already used across project.
---
# Performance
Prefer pagination for list endpoints.
Avoid N+1 queries.
Use fetch join only when needed.
Never optimize prematurely.
---
# Security
Never hardcode:
- Password
- Secret
- API Key
- JWT Secret
Use environment variables.
Never log sensitive information.
---
# Before Finishing Any Task
Agent must:
1. Build project
2. Run tests
3. Fix compilation errors
4. Check formatting
5. Ensure no unused imports
6. Ensure no warnings introduced
---
# Pull Request Checklist
Before submitting:
- Tests pass
- No compilation errors
- No duplicated code
- No TODO left
- Documentation updated if API changes
---
# Things Never To Do
❌ Modify pom.xml unless necessary.
❌ Break package structure.
❌ Rename public APIs without reason.
❌ Ignore failing tests.
❌ Disable validation.
❌ Commit generated files unless required.
---
# Preferred Code Style
Prefer:
Small methods.
Constructor Injection.
Immutable DTO.
Composition over inheritance.
Readable code over clever code.
If uncertain, choose readability.