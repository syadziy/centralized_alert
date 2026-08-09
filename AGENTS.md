# AGENTS.md

## Project Overview

`centralized_alert` adalah service terpusat untuk menerima, menyimpan, dan mengirim alert email
dari service lain melalui REST API dan Kafka.

Stack utama:

- Java 21
- Spring Boot 4.1.0
- Maven Wrapper
- Spring MVC dan Jakarta Bean Validation
- Spring JDBC (`JdbcTemplate` dan `NamedParameterJdbcTemplate`)
- PostgreSQL dan Flyway
- Spring Kafka dengan retry dan dead-letter topic
- Jakarta Mail dan Thymeleaf
- Virtual threads untuk pengiriman paralel
- Actuator, Prometheus, dan ECS structured logging dari `sdk-util`
- JUnit 5 dan Testcontainers

Alur utama service:

1. Alert dibuat melalui REST atau Kafka.
2. Data alert, recipient, dan attachment disimpan secara idempotent di PostgreSQL.
3. Scheduler atau endpoint manual melakukan claim terhadap alert yang siap diproses.
4. Pengiriman berjalan paralel melalui virtual-thread executor.
5. Hasil pengiriman dan retry history disimpan; Kafka failure yang exhausted dikirim ke DLT.

Prioritas desain:

- Durable dan idempotent.
- Aman terhadap concurrent workers.
- Observable melalui structured log, trace ID, metrics, dan health checks.
- Error pada HTTP, Kafka, scheduler, dan asynchronous task ditangani pada boundary yang tepat.
- Perubahan database backward-compatible dan selalu dimigrasikan melalui Flyway.

---

## Project Structure

```text
src/main/java/com/mac/alert/
├── config/                 # Spring beans, Kafka, Jackson, template, virtual thread
│   └── properties/         # Type-safe alert configuration properties
├── controller/             # REST endpoints and response mapping
├── entities/
│   ├── constant/           # Enums, error codes, and structured-log fields
│   ├── dto/                # REST/Kafka request and response records
│   ├── mapper/             # Request-to-command normalization
│   └── model/              # Internal domain/persistence models
├── job/                    # Scheduled pickup boundary
├── repository/
│   └── impl/               # JDBC persistence implementation
├── service/
│   └── impl/               # Alert creation, dispatch, email, template, attachment logic
├── subscriber/             # Kafka listener boundary
└── utils/
    ├── exception/          # Alert-specific exceptions
    └── handler/            # Async/scheduler/virtual-thread exception logging

src/main/resources/
├── db/migration/           # Versioned Flyway migrations
├── templates/              # Email templates
├── json/                   # Example payloads
├── application.yaml
└── application-local.yaml

src/test/java/com/mac/alert/ # Unit and integration tests
```

Shared response, HTTP exception handling, security, OpenAPI, timezone, logging, and MDC utilities
come from the sibling `sdk_util` project through `com.mac:sdk-util`.

---

## Development Commands

Run commands from the `centralized_alert` directory.

### Prepare local sdk-util

Required after changing the sibling SDK or when version `1.0.0` is not available locally:

```bash
cd ../sdk_util
mvn clean install
cd ../centralized_alert
```

### Build

```bash
./mvnw clean verify
```

### Run

```bash
./mvnw spring-boot:run
```

The local profile expects PostgreSQL, Kafka, SMTP, and the configured OAuth2 issuer to be
available. Use `.env.example` as the environment-variable reference; do not treat its development
values as production credentials.

### Test

```bash
./mvnw test
```

### Run a specific test

```bash
./mvnw -Dtest=AlertCreateServiceTest test
```

---

## Coding Guidelines

### Java Version

Use Java 21 features where they improve clarity:

- Records for immutable requests, responses, commands, and value models.
- Pattern matching and switch expressions for closed decision trees.
- `Optional` only as a return type when absence is expected.
- Stream API for readable transformations, not deeply nested control flow.
- Virtual threads only through the configured `alertVirtualThreadExecutor`.

Avoid:

- Legacy `Date`, `Calendar`, and implicit system time; use `Instant`, `Duration`, and injected
  `Clock`.
- Raw types and unchecked casts.
- Mutable DTOs without a framework requirement.
- Creating unmanaged executors or threads inside business methods.
- Catching an exception merely to suppress it.

Preserve interruption: when catching `InterruptedException`, call
`Thread.currentThread().interrupt()` before returning or propagating.

Use UTC for the JVM, JDBC session, persistence, logs, and API timestamps. Convert to a regional
timezone only at an explicit presentation or business-scheduling boundary.

---

## Naming Convention

- Class, record, and enum: `PascalCase`.
- Method and variable: `camelCase`.
- Constants: `UPPER_SNAKE_CASE`.
- Packages: lowercase and under `com.mac.alert`.
- REST DTOs: suffix `Request` or `Response`.
- Kafka payloads: suffix `Event` or a clearly established event name.
- Configuration records/classes: suffix `Properties`.
- Business exceptions: suffix `Exception`.
- Structured fields specific to this service belong in `AlertLogFields`.

Do not introduce alternative package roots or rename public event/DTO fields without checking
consumer compatibility.

---

## Spring Guidelines

Controllers must:

- Validate and deserialize input.
- Delegate business decisions to services.
- Return `ResponseDTO` through `ResponseHelper`.
- Put `@PreAuthorize` with `PERM_<resource>:<action>` on protected endpoint methods. Service
  implementations must not carry HTTP endpoint authorization annotations.
- Remain free of persistence, SMTP, Kafka, and retry logic.

Services own business orchestration. Repository implementations own SQL and result mapping.
Schedulers and Kafka subscribers are inbound boundaries, not containers for business logic.

Use constructor injection. Do not use field injection.

Configuration rules:

- Add alert settings under `alert.*` and map groups to `@ConfigurationProperties`.
- Environment-specific values belong in YAML placeholders or environment variables.
- Organize every application YAML by major property group and precede each group with the
  three-line banner used in this repository (`# =========================`, an uppercase section
  name, and the same separator). Separate sections with one blank line and never change property
  hierarchy merely for formatting.
- Reuse existing beans for `Clock`, `ObjectMapper`, template engine, Kafka error handling, and
  virtual-thread executor.
- Do not duplicate SDK auto-configuration in `com.mac.alert`.
- Keep scheduler and Kafka feature switches conditional on their existing properties.

---

## Entity Rules

This project currently uses JDBC models, not JPA `@Entity` classes. Do not add JPA entities merely
because `spring-boot-starter-data-jpa` is present.

- Never return internal models directly from REST endpoints.
- Keep REST/Kafka DTOs separate from database/domain models.
- Prefer immutable records for models that do not need mutation.
- Preserve enum names persisted in the database; renaming them can break stored data.
- Validate at the boundary and enforce critical invariants again in the service/database layer.

---

## Mapper

`AlertMapper` intentionally performs manual normalization, defaulting, and request-to-command
conversion. Keep mapping there when it includes operations such as trimming, lowercase email
normalization, default priority, default retry count, or default schedule time.

Do not add MapStruct unless the project adopts it explicitly and the change provides clear value.
Trivial response construction may remain near the controller; reusable or policy-heavy mapping
belongs in a mapper.

---

## Database

Use `JdbcTemplate` or `NamedParameterJdbcTemplate` and parameterized SQL. SQL constants belong in
`com.mac.alert.utils.QueryUtil` until a deliberate repository-query refactor is made.

Database rules:

- Every schema change requires a new versioned Flyway migration.
- Never edit a migration that may already have run in another environment.
- Never use string concatenation for request-derived SQL values.
- Preserve transaction boundaries used for claiming, success, and failure updates.
- Keep claim operations safe for multiple service instances and concurrent workers.
- Verify affected row counts when exactly one state transition is expected.
- Maintain idempotency semantics for `sourceSystem` and `idempotencyKey`.
- Avoid N+1 queries and unbounded result sets.

Do not use Hibernate schema generation; `ddl-auto` must remain `none`.

---

## Error Handling

HTTP exceptions are standardized by `GlobalExceptionHandler` from `sdk-util`. Use
`com.mac.sdk_util.exception.ResourceNotFoundException` for a standardized 404 response and
specific domain exceptions for other cases.

Rules by execution boundary:

- REST: allow controller/service exceptions to reach the SDK `@ControllerAdvice` unless a
  service-specific handler is required.
- Kafka: use the configured `CommonErrorHandler`, retry policy, deserialization protection, and
  dead-letter topic.
- Scheduler: catch at the scheduled-job boundary and delegate to `AsyncExceptionHandler`.
- Virtual thread/Future: capture the exception at the task boundary, preserve MDC, and inspect
  `Future` results.
- SMTP/storage: classify failures through the established delivery failure flow.

Broad `catch (Exception)` is allowed only at a top-level asynchronous/integration boundary where
it records or translates the failure. Do not broadly catch inside normal business logic.

Treat `IllegalArgumentException` as HTTP 400 only when it genuinely represents invalid client
input. Error messages returned to clients must be in English and must not expose stack traces,
credentials, SQL, filesystem paths, or provider secrets.

---

## Logging

Use `StructuredLog` and ECS-compatible fields. Do not use `System.out`, `System.err`, or ad-hoc
concatenated business logs.

Required practices:

- Use `LogFields` for common ECS fields and `AlertLogFields` for alert-specific fields.
- Include stable identifiers such as `alert.id`, Kafka topic/partition/offset, trigger source, and
  attempt number when available.
- Use `event.action`, `event.outcome`, and a meaningful `event.dataset`.
- Pass the exception object to error logging so stack traces remain available internally.
- Never log email bodies, access tokens, passwords, attachment content, or unnecessary recipient
  personal data.

HTTP trace IDs are populated by the SDK filter. Kafka, scheduler, and virtual-thread work must
propagate or create `trace.id` explicitly. MDC does not propagate automatically across threads;
copy it before scheduling and run work inside `StructuredLog.withMdc`.

AOP service logging is controlled by `sdk.logging.aspect.*`; keep target packages aligned with the
actual service/repository packages.

---

## Validation

Use Jakarta Bean Validation on inbound REST DTOs and `@Valid` in controllers. Kafka payloads must
also be explicitly validated by the listener because they do not pass through Spring MVC.

- Use field constraints for syntax and size limits.
- Use service validation for cross-field and state-dependent business rules.
- Keep validation messages in English.
- Do not rely solely on validation for database concurrency or idempotency.
- Attachment count, size, storage type, recipient limits, priority, and retry configuration must
  respect the existing `alert.*` properties.

---

## Testing

Every behavior change must include focused tests.

### Unit Test Coverage Standard

- Unit-test line coverage must be at least 90% for production business code.
- New or changed business logic must maintain at least 90% line coverage before a task is
  considered complete.
- Measure coverage with JaCoCo when reporting or enforcing the percentage; never estimate coverage
  from the number of tests.
- Coverage scope must include controllers, services, repositories, mappers, jobs/subscribers,
  exception handlers, and business utilities. Pure DTOs, enums, generated code, and trivial Spring
  bootstrap/configuration classes may be excluded when the exclusion is explicit and justified.
- Do not add meaningless assertions, invoke code without verifying outcomes, or weaken exclusions
  merely to reach the target. Cover success, validation, failure, retry, concurrency, and boundary
  behavior according to the risk of the changed code.
- If the 90% target cannot be verified because JaCoCo is not configured or a required external
  dependency is unavailable, report that limitation explicitly; do not claim the target passed.

- Unit-test mapper normalization, retry calculation, failure classification, and service rules.
- Use integration tests for JDBC queries, Flyway migrations, transaction/state transitions, REST
  contracts, Kafka retry/DLT behavior, or serialization changes.
- Prefer Testcontainers for PostgreSQL and Kafka integration tests.
- Test duplicate/idempotent creation and concurrent claim behavior when those paths change.
- Test both success and failure paths, including retryable versus non-retryable failures.
- Avoid tests that depend on wall-clock time; inject `Clock` or use fixed instants.

Do not claim a coverage percentage unless it was produced by the configured coverage tool. The
90% minimum complements, rather than replaces, meaningful behavioral assertions.

---

## API Design

Current REST contract uses:

- `POST /api/v1/alert`
- `POST /api/v1/alert/{alertId}/dispatch`

Preserve existing routes and response envelopes unless an API version change is intentional.

- Return `ResponseDTO` via `ResponseHelper`.
- Use correct HTTP status codes and a `Location` header for created resources.
- Never expose database models.
- Keep REST messages in English.
- Treat Kafka topics and event schemas as public integration contracts.
- Make event changes backward-compatible or introduce an explicit migration/versioning strategy.

---

## Performance

- Keep list/database operations bounded and paginated where applicable.
- Preserve `batch-size`, processing timeout, and maximum parallelism controls.
- Use the configured virtual-thread executor; do not create one executor per request or alert.
- Avoid holding a database transaction open during SMTP or filesystem I/O.
- Keep SQL indexes aligned with claim, idempotency, status, and retry queries.
- Do not load attachment content or large payloads into logs.
- Measure before changing concurrency, JDBC pool, Kafka concurrency, or retry defaults.

---

## Security

Never hardcode or commit production passwords, API keys, JWTs, SMTP credentials, private keys, or
real customer data. Use environment variables and secret management.

- Keep OAuth2 issuer/JWK configuration externalized.
- Do not declare or override `sdk.security.permit-all-paths` in this service. The canonical public
  path policy is owned by `sdk_util`; propose and test changes there because they affect every SDK
  consumer.
- Restrict CORS explicitly for production rather than relying on permissive SDK defaults.
- Validate attachment paths and prevent directory traversal.
- Do not trust Kafka JSON packages more broadly than required.
- Do not return internal exception details in API responses.

Security-sensitive changes require tests for unauthenticated, unauthorized, and permitted cases.

---

## Before Finishing Any Task

The agent must:

1. Inspect `git status` and preserve unrelated user changes.
2. Compile and run the smallest relevant tests during development.
3. Run `./mvnw test` for completed code changes when dependencies/services permit it.
4. Run `./mvnw clean verify` for higher-risk or cross-layer changes.
5. Run `git diff --check`.
6. Remove unused imports and temporary files.
7. Confirm API/Kafka/database compatibility.
8. Update README, example configuration, and migrations when behavior or configuration changes.
9. Report any test that could not run and the exact external dependency or permission blocking it.

Existing unrelated warnings are not a reason to rewrite unrelated code, but do not introduce new
warnings.

---

## Pull Request Checklist

- Tests for changed behavior pass.
- Project compiles without new warnings or errors.
- Database changes include a new Flyway migration.
- REST and Kafka contracts remain compatible or are explicitly versioned.
- Structured logs contain useful fields and no sensitive payloads.
- Async boundaries preserve trace context and surface failures.
- No duplicated SDK response, security, logging, or exception infrastructure.
- No generated `target/` content, secrets, or local attachment data is committed.
- Documentation and `.env.example` are updated when configuration changes.
- No unexplained TODO or dead code remains.

---

## Things Never To Do

- Do not modify `pom.xml` without a concrete dependency/build requirement.
- Do not replace `spring-boot-starter-kafka` with direct `spring-kafka` in this Boot application.
- Do not bypass Flyway with Hibernate auto-DDL or manual production schema edits.
- Do not acknowledge/drop Kafka failures without the configured retry/DLT policy.
- Do not silently swallow scheduler, Future, or virtual-thread exceptions.
- Do not lose MDC when crossing asynchronous boundaries.
- Do not expose entities/models, stack traces, or secrets through APIs.
- Do not weaken validation, idempotency, path safety, or state-transition guards.
- Do not rename persisted enums, event fields, topics, or public endpoints casually.
- Do not use destructive Git or database commands unless explicitly requested and targets are
  verified.

---

## Preferred Code Style

Prefer:

- Small cohesive methods.
- Constructor injection.
- Immutable records and collections where practical.
- Explicit boundary handling.
- Parameterized SQL.
- Composition over inheritance.
- Domain names that match the alert lifecycle.
- Readable code over clever code.

Match the surrounding formatting when editing an existing file. Avoid broad mechanical reformatting
unless formatting is the task.
