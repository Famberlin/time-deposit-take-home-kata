# Kotlin implementation plan

Ktor service in `kotlin/` with the same two REST endpoints, database schema and API contract as the [Java plan](java-implementation-plan.md), without changing the `TimeDeposit` API or the behaviour of `TimeDepositCalculator.updateBalance`. Requirements: [README](../README.md). Where this plan says nothing, the Java plan applies.

## Constraints

- `TimeDeposit` stays a `data class` with `id`, `planType`, `var balance: Double`, `days`.
- `updateBalance(xs: List<TimeDeposit>)` keeps its signature and results (interest table and HALF_UP rounding as in the Java plan).
- Exactly two endpoints. Invalid input handling is out of scope.
- Only `kotlin/` changes; `java/` stays as is. Build stays Maven so the existing CI matrix keeps working.

## Stack

| Concern | Choice | Spring Boot counterpart |
| --- | --- | --- |
| Server | Ktor 3.x, Netty engine, `ContentNegotiation` + kotlinx.serialization | Spring Web |
| DB access | Exposed 1.x (DSL API, JDBC) + HikariCP + PostgreSQL JDBC driver | Spring Data JPA |
| Schema | Flyway, run on startup before routes are installed | Flyway |
| API contract | Same OpenAPI 3.1 YAML; `openapi-generator-maven-plugin` (`kotlin`, models only, kotlinx_serialization); Ktor `swaggerUI` plugin serves it | openapi-generator + springdoc |
| Wiring | Constructor injection in `Application.module()`, no DI framework | Spring context |
| Health | Own `health`/`info` routes on a second connector, `local`/`dev` only | Actuator |
| Tests | JUnit 6, AssertJ, Ktor `testApplication`, Testcontainers PostgreSQL | same + `@WebMvcTest`/`@DataJpaTest` |
| Architecture rules | Konsist | ArchUnit |
| Formatting | Spotless with ktlint | Spotless with palantir/google-java-format |
| Packaging | Shaded jar (`maven-shade-plugin`), Dockerfile, compose | Spring Boot layered jar |

All versions pinned exactly in `pom.xml`.

**Why Exposed:** it is the JetBrains-maintained Kotlin SQL library, reached 1.0 with a stable API, and integrates with Ktor coroutines through `suspendTransaction`. The DSL maps one-to-one to the two tables with typed columns (`decimal(19, 2)`, `date`), with no entity lifecycle, lazy loading or annotation magic. That is a better fit for two tables and two queries than a JPA stack, and Flyway still owns the schema, so Exposed never creates tables. Rejected: jOOQ (strong, but needs code generation from a live schema in the build), JDBI and Spring JDBC (more hand-written mapping, less Kotlin-idiomatic), Hibernate (heavy without Spring and the reason for leaving Spring).

## Architecture

One Maven module, hexagonal packages, mirroring Java. Konsist tests keep `domain` and `application` free of Ktor, Exposed and adapter imports.

```
org.ikigaidigital
  TimeDeposit, TimeDepositCalculator   existing API, unchanged
  domain/interest/                     InterestPolicy (fun interface), Basic/Student/Premium policies, InterestPolicies registry
  application/port/in/                 UpdateAllBalancesUseCase, ListTimeDepositsUseCase
  application/port/out/                TimeDepositRepository (loadAllWithWithdrawals, lockBatchForUpdate, saveBalances), Transactor
  application/                         TimeDepositService, read model (TimeDepositView, WithdrawalView)
  adapter/in/web/                      Ktor routes, DTO mapping to generated models
  adapter/out/persistence/             Exposed tables, repository, ExposedTransactor
  Application.kt                       main, module(): config, DataSource, Flyway, wiring, plugins, routes
```

- **Calculator:** same Strategy + registry refactor as Java. Default constructor argument wires the default policies, so `TimeDepositCalculator()` still works.
- **`days` does not advance**; KDoc on `updateBalance` with a `TODO`, as in Java.
- **Update flow:** in batches of `timeDeposits.balanceUpdate.batchSize` (default 500), each in its own transaction: lock and load the next batch (`SELECT ... FOR UPDATE`, `id > last id`, ordered by id), call `updateBalance`, save balances, commit. Same as Java.
- **Transactions:** without Spring's `@Transactional`, the service wraps each batch in `Transactor.inTransaction { }`, a port implemented with Exposed `suspendTransaction` on `Dispatchers.IO`. The application layer never imports Exposed.
- **Row locking:** `lockBatchForUpdate` uses Exposed `.forUpdate()`, so concurrent `PUT`s wait for each other per batch and never apply one month twice to the same balance, while the rest of the table stays unlocked. Locks are taken in id order to avoid deadlocks. `GET` takes no locks. Trade-off: a run is not atomic; if it fails, committed batches keep their new balances.
- **Withdrawals:** GET loads deposits and withdrawals in two queries and groups them in memory (no N+1). Not used in interest calculation.
- `Double` ↔ `BigDecimal(scale 2)` conversion at the persistence boundary.

## Database

Identical to Java: same Flyway scripts in `src/main/resources/db/migration` (`V1__create_time_deposits.sql`, `V2__create_withdrawals.sql`, `R__seed_demo_data.sql` only for `local`), snake_case tables `time_deposits` and `withdrawals`, `plan_type` free text. Copied, not shared, so each project builds alone.

## API

Same contract as Java, copied to `src/main/resources/openapi/time-deposits.yaml`:

| Method | Path | Result |
| --- | --- | --- |
| `GET` | `/api/v1/time-deposits` | `200` `TimeDepositPage` `{ items, total }`; pagination `TODO` |
| `PUT` | `/api/v1/time-deposits/balances` | `204`; applies one month of interest (batched, concurrent calls serialized by row locks) |

Only models are generated. Routes are hand-written because the generator's Ktor server output is a whole app skeleton, not an interface to implement. A contract test validates real responses against the YAML (`swagger-request-validator`), which replaces the compile-time check Java gets from the generated interface. Swagger UI at `/swagger-ui`.

## Config and profiles

`application.yaml` (Ktor YAML config) with `${?ENV}` overrides for `DB_URL`, `DB_USER`, `DB_PASSWORD`, plus `timeDeposits.balanceUpdate.batchSize`. Profile via `APP_PROFILE` (`local`, `dev`, default none). For `local`/`dev`, the server adds a second connector on port 8081 serving `GET /health` (checks a DB connection) and `GET /info` (version from the jar manifest), so port 8080 only serves the API.

## Testing

| Layer | How |
| --- | --- |
| Calculator | Characterization tests (all plans, days 30/31/45/46/365/366, unknown plan, rounding), written before the refactor |
| Policies, service | Plain JUnit 6; service against an in-memory repository and a no-op transactor |
| Persistence | Testcontainers PostgreSQL + Flyway: migrations, mapping, locking query, two-query loading |
| Web | `testApplication` with fake use cases: paths, status codes, JSON shape; contract validation against the YAML |
| End to end | `testApplication` with the real module + Testcontainers: `PUT` then `GET` across several batches; parallel updates each apply one month; only the current batch is locked |
| Architecture | Konsist layer rules |

Unit tests in `test` (surefire), container tests in `verify` (failsafe, `*IT`). CI already runs `mvn -B verify` for `kotlin`.

## Build and delivery

- **Spotless + ktlint:** same setup as Java: `apply` on `package`, `check` on `verify`; CI passes `-Dspotless.apply.skip=true`. `.editorconfig` holds ktlint settings (`ktlint_official` style, matches `kotlin.code.style=official`).
- **Snyk:** same CI job, extended to `kotlin/` (matrix), failing on high/critical; skipped without `SNYK_TOKEN`.
- **Dockerfile:** multi-stage, Temurin 25 JRE runtime, shaded jar, non-root.
- **compose.yaml:** `postgres:18` with healthcheck + `app` with `APP_PROFILE=local`. Uses host ports 8090/8091 so it can run next to the Java compose stack.

## Delivery steps

Each step is one or a few atomic commits that keep `mvn -B verify` green.

1. Spotless + ktlint (format-only commit), Snyk matrix entry; characterization tests on untouched code.
2. Interest policies; calculator delegates to them; policy tests; Konsist rules.
3. Ktor, Exposed, Flyway, Hikari dependencies; `Application.kt` with config loading.
4. Ports, service, read model, `Transactor`.
5. Flyway migrations and seed data (copied from Java).
6. Persistence adapter + container tests.
7. OpenAPI contract, model generation, routes, Swagger UI + web and contract tests.
8. Health/info connector for `local`/`dev`.
9. End-to-end test.
10. Shade plugin, Dockerfile, `.dockerignore`, compose; verify via Swagger UI.
11. `kotlin/README.md` (run, call, build), design notes; update AGENTS.md project map.

## Decisions (2026-11-07)

| Topic | Decision |
| --- | --- |
| Concurrent `PUT` | Batches of configurable size (default 500), one transaction per batch, locking only that batch's rows (`SELECT … FOR UPDATE`); same as Java |

## Open questions (defaults used unless told otherwise)

| Question | Default |
| --- | --- |
| Netty or CIO engine? | Netty (most mature, best documented) |
| Konsist or ArchUnit for layer rules? | Konsist (source-based, Kotlin-aware); ArchUnit also works on Kotlin bytecode if parity with Java matters more |
| DI framework (Koin or Ktor DI plugin)? | None; a handful of objects wired by hand |
| Share migrations and OpenAPI YAML between `java/` and `kotlin/`? | No, copy them so each project builds and ships alone |
| Docker image via Dockerfile or Jib? | Dockerfile, same as Java |
