# Kotlin implementation plan

Ktor service in `kotlin/` that mirrors the Java service now on `main` ([plan](java-implementation-plan.md), [java/README.md](../java/README.md)): same two endpoints, database schema, API contract, update semantics and test matrix, without changing the `TimeDeposit` API or the behaviour of `TimeDepositCalculator.updateBalance`. Requirements: [README](../README.md). The Java code is the reference: port its classes and test cases one to one unless this plan says otherwise.

## Constraints

- `TimeDeposit` stays a `data class` with `id`, `planType`, `var balance: Double`, `days`.
- `updateBalance(xs: List<TimeDeposit>)` keeps its signature and results (interest table and HALF_UP rounding as in the Java plan).
- Exactly two endpoints. Invalid input handling is out of scope.
- Only `kotlin/` changes, plus CI and docs. Build stays Maven so the existing CI matrix keeps working.

## Stack

| Concern | Kotlin choice | Java on `main` |
| --- | --- | --- |
| Server | Ktor 3.x, Netty engine, `ContentNegotiation` + kotlinx.serialization | Spring Boot 4.1 Web MVC |
| DB access | Exposed 1.x (DSL API, JDBC) + HikariCP + PostgreSQL driver | Spring Data JPA |
| Schema | Flyway, run on startup before routes are installed | Flyway (Boot auto-config) |
| API contract | Same `openapi/time-deposits.yaml`; `openapi-generator-maven-plugin` (`kotlin`, models only, kotlinx_serialization); Ktor `swaggerUI` plugin | openapi-generator interface + springdoc |
| Wiring | `config/` package wires objects by hand in `Application.module()` | Spring `@Configuration` in `config/` |
| Health | Own `health`/`info` routes on a second connector, port 8081, `local`/`dev` only | Actuator on 8081, `local`/`dev` only |
| Tests | JUnit 6, AssertJ, Ktor `testApplication`, Testcontainers PostgreSQL, failsafe for `*IT` | same + `@WebMvcTest`, `@DataJpaTest` |
| Architecture rules | Konsist | ArchUnit |
| Formatting | Spotless with ktlint | Spotless with palantir-java-format |
| Packaging | Shaded jar (`maven-shade-plugin`), multi-stage Dockerfile, compose | Layered Boot jar, Dockerfile, compose |

All versions pinned exactly in `pom.xml`.

**Why Exposed:** it is the JetBrains-maintained Kotlin SQL library, stable since 1.0, and integrates with Ktor coroutines through `suspendTransaction`. The DSL maps one to one to the two tables with typed columns (`decimal(19, 2)`, `date`) and supports `.forUpdate()` for the batch lock, without entity lifecycle, lazy loading or annotations. Flyway still owns the schema, so Exposed never creates tables. Rejected: jOOQ (needs code generation from a live schema in the build), JDBI and Spring JDBC (more hand-written mapping, less idiomatic), Hibernate (heavy without Spring).

## Architecture

Same packages and names as `java/`, enforced by Konsist: `domain` and `application` import nothing from Ktor, Exposed or `adapter`.

```
org.ikigaidigital
  TimeDeposit, TimeDepositCalculator   existing API, unchanged
  Application.kt                       main, module(): config, DataSource, Flyway, plugins, routes
  domain/interest/                     InterestPolicy, AnnualRateInterestPolicy, Basic/Student/Premium policies, InterestPolicies
  application/port/in/                 UpdateAllBalancesUseCase, ListTimeDepositsUseCase
  application/port/out/                TimeDepositRepository (loadAll, lockBatchForUpdate, saveBalances), TransactionRunner
  application/                         TimeDepositService, read model (TimeDepositView, WithdrawalView)
  adapter/in/web/                      Ktor routes, mapping to generated models, health/info routes
  adapter/out/persistence/             Exposed tables, TimeDepositPersistenceAdapter, mapper
  config/                              wiring, ExposedTransactionRunner, AppSettings (batch size, profile)
```

- **Calculator:** port of the Java refactor. `InterestPolicy` (`planType`, `monthlyInterest`), abstract `AnnualRateInterestPolicy(planType, annualRate)` keeping `balance * rate / 12` order so doubles match exactly, and `InterestPolicies` with `GRACE_PERIOD_DAYS = 30`, `defaults()` and `of(...)`. `TimeDepositCalculator(policies: InterestPolicies = InterestPolicies.defaults())`, so `TimeDepositCalculator()` still works.
- **`days` does not advance**; KDoc on `updateBalance` with a `TODO`, as in Java.
- **Update flow:** as in Java: batches of `time-deposits.balance-update.batch-size` (default 500), each in its own transaction: `lockBatchForUpdate(afterId, limit)` (`SELECT ... FOR UPDATE`, `id > afterId`, ordered by id), `updateBalance`, `saveBalances`, commit; repeat while a batch is full. Concurrent `PUT`s wait for each other per batch; locks in id order avoid deadlocks; `GET` takes no locks. A run is not atomic: committed batches keep their balances if a later batch fails.
- **Transactions:** `TransactionRunner` port (`inTransaction`, `inReadOnlyTransaction`) as in Java, implemented in `config/` with Exposed `suspendTransaction` on `Dispatchers.IO`. The port's functions are `suspend`, the only signature difference from Java.
- **Read path:** `loadAll()` reads deposits and withdrawals in two queries and groups in memory (no N+1), in a read-only transaction.
- `Double` ↔ `BigDecimal(scale 2)` at the persistence boundary.

## Database

Copy Java's scripts unchanged: `db/migration/V1__create_time_deposits.sql`, `db/migration/V2__create_withdrawals.sql`, and `db/seed/R__seed_demo_data.sql`, which Flyway adds to its locations only for the `local` profile. Copied, not shared, so each project builds alone.

## API

Copy `openapi/time-deposits.yaml` unchanged.

| Method | Path | Result |
| --- | --- | --- |
| `GET` | `/api/v1/time-deposits` | `200` `TimeDepositPage` `{ items, total }`; pagination `TODO` |
| `PUT` | `/api/v1/time-deposits/balances` | `204`; one month of interest, batched, concurrent calls serialized by row locks |

Only models are generated; routes are hand-written because the generator's Ktor output is a whole app skeleton, not an interface to implement. A contract test validates real responses against the YAML (`swagger-request-validator`), replacing the compile-time check Java gets from the generated interface. Swagger UI at `/swagger-ui`, with `/swagger-ui.html` redirecting there so the instructions match Java.

## Config and profiles

`application.yaml` (Ktor YAML config), mirroring Java's keys:

- `db.url`, `db.user`, `db.password`, overridable with `DB_URL`, `DB_USER`, `DB_PASSWORD`.
- `time-deposits.balance-update.batch-size: 500`, overridable with `TIME_DEPOSITS_BALANCE_UPDATE_BATCH_SIZE` (same variable as Java).
- Profile from `APP_PROFILE` (`local`, `dev`, default none). `local` adds `db/seed` to Flyway; `local` and `dev` start the second connector on 8081 with `GET /health` (`{"status":"UP"}` after a DB check) and `GET /info`. Port 8080 serves only the API and Swagger UI.

## Testing

Port Java's test classes and cases with the same inputs and expected values:

| Java on `main` | Kotlin |
| --- | --- |
| `TimeDepositCalculatorTest` (characterization) | extend the existing Kotlin test to the same cases before refactoring |
| `InterestPoliciesTest`, `AnnualRateInterestPoliciesTest` | same, plain JUnit 6 |
| `TimeDepositServiceTest` (in-memory repository fake) | same, with a no-op `TransactionRunner`; `runTest` for suspend calls |
| `TimeDepositControllerTest` (`@WebMvcTest`) | `TimeDepositRoutesTest` with `testApplication` and fake use cases, plus contract validation |
| `TimeDepositPersistenceAdapterIT` | same, Testcontainers PostgreSQL + Flyway: mapping, locking query, two-query loading |
| `TimeDepositApplicationIT` | same, real module + Testcontainers: `PUT` then `GET` across batches, parallel `PUT`s each apply one month |
| `ArchitectureTest` (ArchUnit) | `ArchitectureTest` (Konsist) |
| `TestTimeDepositApplication` (local run with Testcontainers) | `TestApplication.kt` main in test sources: starts PostgreSQL, runs `module()` with `local` |

Unit tests in `test` (surefire), container tests in `verify` (failsafe, `*IT`).

## Build, CI and delivery

- **Spotless + ktlint:** same executions as Java (`apply` on `package`, `check` on `verify`). CI already runs `mvn -B verify -Dspotless.apply.skip=true` for both matrix entries, so nothing changes there. `.editorconfig` sets `ktlint_official`, matching `kotlin.code.style=official`.
- **Snyk:** turn the `snyk (java)` job into a `[java, kotlin]` matrix; same skip without `SNYK_TOKEN`, same high threshold.
- **Dockerfile:** multi-stage like Java (`maven:3.9-eclipse-temurin-25` build, `-DskipTests -Dspotless.apply.skip=true`, `eclipse-temurin:25-jre` runtime, non-root `app` user), running the shaded jar; exposes 8080 and 8081.
- **compose.yaml:** same services as Java with `APP_PROFILE=local` and `DB_*` variables. Host ports 8090/8091 so it can run next to the Java stack.
- **CI smoke test:** turn the `docker (java)` job into a matrix with a port per project; same curl checks (both endpoints, `"total":9` after seed, Swagger UI, health `UP`).

## Delivery steps

Each step is one or a few atomic commits that keep `mvn -B verify` green.

1. Spotless + ktlint (format-only commit), Snyk matrix; characterization tests ported from Java on untouched code.
2. Interest policies; calculator delegates to them; policy tests; Konsist `ArchitectureTest`.
3. Ktor, Exposed, Flyway, Hikari, failsafe; `Application.kt` and config loading.
4. Ports, service, read model, `TransactionRunner`; service tests.
5. Migrations and seed copied from Java.
6. Persistence adapter + `TimeDepositPersistenceAdapterIT`.
7. OpenAPI models, routes, Swagger UI + route and contract tests.
8. Health/info connector for `local`/`dev`.
9. `TimeDepositApplicationIT`; `TestApplication.kt`.
10. Shade plugin, Dockerfile, `.dockerignore`, compose; CI smoke-test matrix.
11. `kotlin/README.md` (run, call, build, like `java/README.md`); Kotlin in the root README's Solution and AI-assisted sections; AGENTS.md project map.

## Decisions (2026-11-07)

| Topic | Decision |
| --- | --- |
| Concurrent `PUT` | Batches of configurable size (default 500), one transaction per batch, locking only that batch's rows (`SELECT … FOR UPDATE`); same as Java |
| Everything else | As in the Java plan's decisions: snake_case tables, `/api/v1` paths, `PUT` update, `{ items, total }`, health only in `local`/`dev`, format locally and check in CI, Snyk, one module with layer rules, `days` not advanced |

## Open questions (defaults used unless told otherwise)

| Question | Default |
| --- | --- |
| Netty or CIO engine? | Netty (most mature, best documented) |
| Konsist or ArchUnit for layer rules? | Konsist (source-based, Kotlin-aware); ArchUnit also works on Kotlin bytecode if parity with Java matters more |
| DI framework (Koin or Ktor DI plugin)? | None; a handful of objects wired by hand in `config/` |
| Share migrations and OpenAPI YAML between `java/` and `kotlin/`? | No, copy them so each project builds and ships alone |
| Health paths: `/health` or Java's `/actuator/health`? | `/health` and `/info`; there is no Actuator in Ktor |
