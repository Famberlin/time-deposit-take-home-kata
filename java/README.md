# Time Deposit service (Java)

Spring Boot service for XA Bank time deposits: lists deposits and applies monthly interest. Design and decisions: [implementation plan](../docs/java-implementation-plan.md).

## Requirements

- JDK 25, Maven 3.9+
- Docker (for compose and Testcontainers)

## Run locally

```bash
cd java
docker compose up --build
```

Then open Swagger UI at <http://localhost:8080/swagger-ui.html>. The `local` profile loads demo data (every plan and the 30/45/365-day edges). Actuator health: <http://localhost:8081/actuator/health>.

Without compose, run `TestTimeDepositApplication` from the IDE (or `mvn spring-boot:test-run`): it starts PostgreSQL with Testcontainers and uses the `local` profile.

## API

Base path `/api/v1`. Contract: [`src/main/resources/openapi/time-deposits.yaml`](src/main/resources/openapi/time-deposits.yaml).

| Method | Path | Does |
| --- | --- | --- |
| `GET` | `/time-deposits` | All deposits with withdrawals, as `{ "items": [...], "total": n }` |
| `PUT` | `/time-deposits/balances` | Adds one month of interest to every balance; returns `204` |

In Swagger UI: open **time-deposits**, pick an operation, **Try it out**, **Execute**. With curl:

```bash
curl http://localhost:8080/api/v1/time-deposits
curl -X PUT http://localhost:8080/api/v1/time-deposits/balances
```

Example `GET` response (shortened to one deposit):

```json
{
  "items": [
    { "id": 2, "planType": "basic", "balance": 10000.00, "days": 31,
      "withdrawals": [ { "id": 1, "amount": 500.00, "date": "2026-09-15" } ] }
  ],
  "total": 1
}
```

Each `PUT` adds another month (it is not idempotent). It works through the deposits in batches (`time-deposits.balance-update.batch-size`, default 500), one transaction per batch, and locks only the rows of the current batch until that batch commits. Concurrent calls wait on those locks, so each applies exactly one month to every deposit. If a call fails, batches already committed keep their new balances. `days` is not advanced.

Interest per month: basic 1%, student 3% (none after 1 year), premium 5% (from day 46); no plan earns interest in the first 30 days.

## Build and test

```bash
mvn package    # build; also auto-formats with Spotless
mvn verify     # unit + Testcontainers integration tests + Spotless check
```

Unit tests (`*Test`) need no Docker; integration tests (`*IT`) do. CI runs `mvn -B verify -Dspotless.apply.skip=true`, a Docker smoke test and, when `SNYK_TOKEN` is set, a Snyk scan.

## Design

Hexagonal, in one Maven module; `ArchitectureTest` (ArchUnit) keeps the core free of Spring, JPA and adapters.

| Package | Contains |
| --- | --- |
| `org.ikigaidigital` | `TimeDeposit`, `TimeDepositCalculator` (public API unchanged) |
| `domain.interest` | One `InterestPolicy` per plan and the `InterestPolicies` registry |
| `application` | `TimeDepositService`, read model, `port.in` use cases, `port.out` repository |
| `adapter.in.web` | Controller implementing the interface generated from the OpenAPI contract |
| `adapter.out.persistence` | JPA entities, Spring Data repository, mapper |
| `config` | Spring wiring, `TransactionRunner` on Spring transactions, settings |

- **Interest:** `updateBalance` delegates to `InterestPolicies` (Strategy + registry). A new plan is a new `InterestPolicy`, registered in `InterestPolicies.defaults()` or passed to `new TimeDepositCalculator(InterestPolicies.of(...))`. Characterization tests pin the original results, including rounding.
- **Assumptions:** `days` is not advanced by an update (original behaviour, `TODO` in the Javadoc); withdrawals are listed but do not affect interest; unknown plan types earn nothing; invalid input is not handled (out of scope).
- **Money:** `NUMERIC(19,2)` in the database and decimal numbers in the API; `TimeDeposit` keeps its `Double` balance, converted at the persistence boundary.

## Database

PostgreSQL, schema managed by Flyway: [`src/main/resources/db/migration`](src/main/resources/db/migration).

## Configuration

| Variable | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | Database connection |
| `TIME_DEPOSITS_BALANCE_UPDATE_BATCH_SIZE` | Deposits per transaction for the balance update (default 500) |
| `SPRING_PROFILES_ACTIVE` | `local` loads demo data; `local` and `dev` enable actuator (`health`, `info` on port 8081) |

## Improvements

- **TODO: idempotent, retry-safe balance update.** Deposits are independent, so updating them together, in batches or one at a time gives the same balances. But `PUT /time-deposits/balances` is not idempotent: every call adds one more (compounding) month, and `days` is not advanced, so the balance depends on how often it is called, not on elapsed time. A run is also not all-or-nothing: retrying after a partial failure gives a second month to the batches already committed. Kept as is until the requirements are clarified. One possible fix: store the last interest period per deposit (for example the month) and skip deposits that already have it, and advance `days` (or derive it from a start date).
- **TODO: pagination** for `GET /time-deposits` (`page`/`size`), non-breaking thanks to the `{ items, total }` wrapper.
