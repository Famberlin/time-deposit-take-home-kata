# Java implementation plan

Spring Boot service in `java/` exposing exactly two REST endpoints over PostgreSQL, without changing the `TimeDeposit` API or the behaviour of `TimeDepositCalculator.updateBalance`. Requirements: [README](../README.md). Day-to-day usage: [java/README.md](../java/README.md).

## Constraints

- `TimeDeposit` keeps its constructor, getters and `setBalance`.
- `updateBalance(List<TimeDeposit>)` keeps its signature and results: `Double` balance, monthly interest rounded HALF_UP to 2 decimals.
- Exactly two endpoints. Invalid input handling is out of scope.
- Only `java/` changes; `kotlin/` stays as is.

Interest rules to preserve:

| Plan | Monthly interest | Applies when |
| --- | --- | --- |
| basic | 1% / 12 | days > 30 |
| student | 3% / 12 | 30 < days < 366 |
| premium | 5% / 12 | days > 45 |
| other | none | never |

## Stack

Spring Boot 4.1.x (Web MVC, Data JPA, Flyway, Actuator), PostgreSQL 18, Flyway, OpenAPI 3.1 contract with `openapi-generator-maven-plugin` + springdoc, Testcontainers, ArchUnit, Spotless, Snyk, Docker, docker compose. Versions pinned exactly in `pom.xml`.

## Architecture

One Maven module, hexagonal packages; ArchUnit keeps `domain` and `application` free of Spring, JPA and adapter imports.

```
org.ikigaidigital
  TimeDeposit, TimeDepositCalculator   existing API, unchanged
  domain/interest/                     InterestPolicy + Basic/Student/Premium policies, InterestPolicies registry
  application/port/in/                 UpdateAllBalancesUseCase, ListTimeDepositsUseCase
  application/port/out/                TimeDepositRepository (loadAll, lockAllForUpdate, saveBalances)
  application/                         TimeDepositService, read model (TimeDepositView, WithdrawalView)
  adapter/in/web/                      controller implementing the generated OpenAPI interface
  adapter/out/persistence/             JPA entities, Spring Data repositories, mappers
  config/                              Spring wiring; transactions around the framework-free service
```

- **Calculator:** the plan-type `if` chain becomes one `InterestPolicy` per plan (Strategy + registry); unknown plans earn zero. The no-arg constructor wires the defaults, so callers are unaffected; a second constructor takes custom policies. The shared "first 30 days" rule is applied once.
- **`days` does not advance** on update, so repeated updates never cross a threshold. Kept as is; documented in the `updateBalance` Javadoc with a `TODO`.
- **Withdrawals** are returned by GET but not used in interest calculation.
- **Update flow:** lock and load all deposits (`SELECT ... FOR UPDATE`, ordered by id), map to `TimeDeposit`, call `updateBalance`, save balances, in one transaction. `Double` ↔ `BigDecimal(scale 2)` at the persistence boundary.
- **Row locking:** the `PUT` locks every affected row with `PESSIMISTIC_WRITE` until commit, so concurrent updates wait for each other instead of reading the same balance and applying one month twice (lost update). Locks are taken in id order to avoid deadlocks; the locking query requires a transaction. `GET` takes no locks.

## Database

Flyway owns the schema (`src/main/resources/db/migration`); Hibernate runs with `ddl-auto=validate`. README names map to snake_case.

- `V1__create_time_deposits.sql`: `time_deposits(id, plan_type, days, balance NUMERIC(19,2))`, all `NOT NULL`.
- `V2__create_withdrawals.sql`: `withdrawals(id, time_deposit_id FK, amount NUMERIC(19,2), date DATE)`, all `NOT NULL`, indexed on `time_deposit_id`.
- `R__seed_demo_data.sql`: demo data incl. the 30/45/365-day edges, loaded only for the `local` profile.

`plan_type` is free text (no `CHECK`), so a new plan needs no migration.

## API

Contract first: `src/main/resources/openapi/time-deposits.yaml` is the source of truth for paths and schemas; Swagger UI at `/swagger-ui.html`. Versioned by URI prefix `/api/v1`.

| Method | Path | Result |
| --- | --- | --- |
| `GET` | `/api/v1/time-deposits` | `200` `TimeDepositPage` |
| `PUT` | `/api/v1/time-deposits/balances` | `204`; applies one month of interest to every deposit (not idempotent; concurrent calls serialized by row locks) |

`TimeDepositPage` wraps the list so paging can be added without a breaking change:

```json
{
  "items": [
    { "id": 1, "planType": "basic", "balance": 1234.56, "days": 45,
      "withdrawals": [ { "id": 7, "amount": 100.00, "date": "2026-10-01" } ] }
  ],
  "total": 1
}
```

`total` is the number of deposits. Pagination (`page`/`size` query params, `page`/`size` fields) is a later step, marked `TODO` in the contract.

Actuator is off by default; `local` and `dev` profiles expose `health` and `info` on management port 8081, so the API port only serves the two endpoints.

## Testing

| Layer | How |
| --- | --- |
| Calculator | Characterization tests (all plans, days 30/31/45/46/365/366, unknown plan, rounding), written before the refactor |
| Policies, service | Plain JUnit 6; service against an in-memory repository fake |
| Persistence | `@DataJpaTest` + Testcontainers PostgreSQL: migrations, mapping, locking query, no N+1 on withdrawals |
| Web | `@WebMvcTest`: paths, status codes, JSON shape |
| End to end | `@SpringBootTest` + Testcontainers: `PUT` then `GET`; parallel updates each apply one month; rows locked until commit |
| Architecture | ArchUnit layer rules |

Unit tests run in `test` (surefire), container tests in `verify` (failsafe, `*IT`). CI already runs `mvn -B verify`.

## Build and delivery

- **Spotless:** `apply` on `package` (auto-format locally), `check` on `verify`. CI runs `-Dspotless.apply.skip=true`, so the pipeline only checks.
- **Snyk:** CI job on `java/` failing on high/critical; container scan once the image exists. Needs the `SNYK_TOKEN` secret; skipped without it.
- **Dockerfile:** multi-stage, Temurin 25 JRE runtime, layered jar, non-root, config via `SPRING_DATASOURCE_*` env vars.
- **compose.yaml:** `postgres:18` with healthcheck + `app` built from the Dockerfile, `local` profile.
- **CI smoke test:** a `docker` job builds the image with compose and checks both endpoints, Swagger UI and actuator health.

## Delivery steps

Each step is one or a few atomic commits that keep `mvn -B verify` green.

1. Spotless (format-only commit) and Snyk job; characterization tests on untouched code.
2. Interest policies; calculator delegates to them.
3. Policy unit tests; ArchUnit rules.
4. Spring Boot and dependencies; application class.
5. Ports, service, read model.
6. Flyway migrations and seed data.
7. Persistence adapter + `@DataJpaTest`.
8. OpenAPI contract, generated interface, controller + `@WebMvcTest`.
9. End-to-end test; `TestTimeDepositApplication` for local runs.
10. Dockerfile, `.dockerignore`, compose; verify via Swagger UI.
11. READMEs: run instructions, design notes, AI-assisted development section; update AGENTS.md project map.

## Decisions (2026-11-07)

| Topic | Decision |
| --- | --- |
| Tables | snake_case |
| Endpoints | `GET /api/v1/time-deposits`, `PUT /api/v1/time-deposits/balances` |
| GET response | `{ items, total }` wrapper; pagination later |
| Actuator | Off by default; on for `local`/`dev` |
| Layout | One module + ArchUnit |
| Quality | Spotless (format locally, check in CI), Snyk |
| `days` | Not advanced; Javadoc `TODO` |
| Branching | Always from latest `main` |
| Concurrency | `PUT` locks the affected rows (`SELECT ... FOR UPDATE`) |
