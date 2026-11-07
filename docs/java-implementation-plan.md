# Time deposit kata: Java implementation plan

As of 2026-11-07. Source of truth while drafting: [Claude Doc](https://claude.ai/artifact/Tj7zTcJJJGUBSRczDT98fV).

## Goal and constraints

Turn `java/` into a Spring Boot service with exactly two REST endpoints backed by PostgreSQL, while the existing domain classes keep their public API and `updateBalance` keeps its exact behaviour. The plan builds on `main`, which already has the JDK 25, JUnit 6 and CI upgrade (PR #2, merged).

Hard constraints from the README and AGENTS.md:

- `TimeDeposit` keeps its constructor, getters and `setBalance`; no breaking changes.
- `TimeDepositCalculator.updateBalance(List<TimeDeposit>)` keeps its signature and results, including the `Double` balance and HALF_UP rounding of interest to 2 decimals.
- Exactly two endpoints: update all balances, and list all deposits (`id`, `planType`, `balance`, `days`, `withdrawals`).
- Tables `timeDeposits` and `withdrawals` with the columns the README lists.
- Design must be extensible for future interest rules (SOLID, hexagonal architecture preferred).
- Only `java/` changes; `kotlin/` stays as it is.

Current rules, as coded (the behaviour to preserve):

| Plan | Monthly interest | Applies when |
| --- | --- | --- |
| basic | balance × 1% / 12 | days > 30 |
| student | balance × 3% / 12 | 30 < days < 366 |
| premium | balance × 5% / 12 | days > 45 |
| any other | none | never |

## Architecture

One Maven module with hexagonal packages: the domain and application core have no Spring or JPA imports, and adapters plug in from the outside. An ArchUnit test enforces the dependency direction, which keeps the module simple without losing the boundaries.

```
org.ikigaidigital
  TimeDeposit                      unchanged public API
  TimeDepositCalculator            same signature; delegates to interest policies
  domain/interest/
    InterestPolicy                 interface: supports(planType), monthlyInterest(deposit)
    BasicInterestPolicy            1%, after 30 days
    StudentInterestPolicy          3%, days 31..365
    PremiumInterestPolicy          5%, after 45 days
    InterestPolicies               registry; unknown plan -> zero interest
  application/
    port/in/  UpdateAllBalancesUseCase, ListTimeDepositsUseCase
    port/out/ TimeDepositRepository (loadAll, saveBalances)
    TimeDepositService             implements both use cases, @Transactional at adapter edge
    TimeDepositView + WithdrawalView   read model for the GET endpoint
  adapter/in/web/                  controller implementing the generated OpenAPI interface
  adapter/out/persistence/         JPA entities, Spring Data repos, mapper
  TimeDepositApplication           @SpringBootApplication
```

Key decisions:

- **Calculator refactor (Strategy + registry).** `updateBalance` keeps its loop and rounding, but the plan-type `if` chain moves into one `InterestPolicy` per plan. A new plan or rule is a new class, not an edit (open/closed). The no-arg constructor stays and wires the three default policies, so existing callers compile unchanged; a second constructor accepts a custom list.
- **Shared 30-day rule.** A small base class or decorator applies the "no interest in the first 30 days" rule once, so each policy only states its own threshold.
- **Days do not advance.** `updateBalance` applies one month of interest but leaves `days` unchanged, so repeated calls never cross the 30/45/365-day thresholds. This stays as is; the Javadoc of `updateBalance` states it with a `TODO` for a future change.
- **Withdrawals are read-only.** They appear in the GET response, but the current interest rules do not use them, so they stay out of the calculator. A future rule can read them through the policy interface.
- **Update flow.** The service loads all deposits, maps them to `TimeDeposit`, calls `updateBalance`, then writes the new balances back in one transaction. `Double` is converted to `BigDecimal` scale 2 at the persistence boundary.

## Database and Flyway

Flyway owns the schema; Hibernate runs with `ddl-auto=validate` so drift fails at startup. Migrations live in `src/main/resources/db/migration`.

| Migration | Content |
| --- | --- |
| `V1__create_time_deposits.sql` | `time_deposits (id SERIAL PK, plan_type VARCHAR(50) NOT NULL, days INTEGER NOT NULL, balance NUMERIC(19,2) NOT NULL)` |
| `V2__create_withdrawals.sql` | `withdrawals (id SERIAL PK, time_deposit_id INTEGER NOT NULL FK -> time_deposits(id), amount NUMERIC(19,2) NOT NULL, date DATE NOT NULL)` + index on `time_deposit_id` |
| `R__seed_demo_data.sql` | A few deposits per plan type (including the 30/45/365-day edges) and some withdrawals; loaded only with the `local` profile via an extra Flyway location |

Notes:

- Table and column names use Postgres snake_case (`time_deposits`, `plan_type`), mapped from the README's `timeDeposits` / `planType`. Quoted camelCase identifiers would work but make every hand-written query awkward; the mapping is documented in a code comment.
- `id` is `INTEGER` to match the README and the `int id` in `TimeDeposit`.
- `plan_type` stays a free string, as in the domain, so a new plan needs no migration. A `CHECK` constraint is deliberately left out for the same reason.
- `days` is stored, not derived from a start date, because the domain model has no start date.

## REST API (contract first)

The OpenAPI 3.1 contract `src/main/resources/openapi/time-deposits.yaml` is the source of truth. `openapi-generator-maven-plugin` (spring generator, `interfaceOnly=true`) generates the API interface and DTOs at build time; the controller implements it. springdoc serves Swagger UI at `/swagger-ui.html` so reviewers can trigger both endpoints from the browser.

| Method | Path | Purpose | Response |
| --- | --- | --- | --- |
| `GET` | `/api/v1/time-deposits` | List all deposits with their withdrawals | `200` array of `TimeDeposit` |
| `PUT` | `/api/v1/time-deposits/balances` | Apply one month of interest to every deposit | `204 No Content` |

Response schema for `GET`:

```
TimeDeposit:  id (int32), planType (string), balance (number, 2 dp), days (int32),
              withdrawals: [ Withdrawal ]
Withdrawal:   id (int32), amount (number, 2 dp), date (date)
```

- Versioning by URI prefix: both paths sit under `/api/v1`, set once as the contract's server URL, so a breaking change can ship later as `/api/v2` beside v1. The update uses `PUT` as agreed; calling it twice still adds interest twice.
- Actuator is on the classpath but disabled by default (`management.endpoints.access.default=none`, nothing exposed). The `local` and `dev` profiles turn on `health` and `info` on a separate management port (8081), so the API port always serves exactly the two endpoints.
- Invalid input handling is out of scope per the README; the endpoints take no request body anyway.
- The README gets a "How to call the API" section: start compose, open Swagger UI, plus two `curl` examples.

## Testing

Tests go bottom-up, and the calculator gets a characterization safety net before any refactoring touches it.

| Layer | Tool | What it proves |
| --- | --- | --- |
| Calculator characterization | JUnit 6 `@ParameterizedTest`, AssertJ | Exact balances for every plan at days 30, 31, 45, 46, 365, 366, an unknown plan, and rounding cases. Written against the current code first, then kept green through the refactor |
| Interest policies | JUnit 6 | Each policy in isolation, plus the registry's fallback to zero |
| Application service | JUnit 6 + in-memory fake of `TimeDepositRepository` | Load, calculate, save flow without Spring |
| Persistence adapter | `@DataJpaTest` + Testcontainers PostgreSQL (`@ServiceConnection`) | Flyway migrations apply, entity mapping, `saveBalances`, fetch of withdrawals without N+1 |
| Web adapter | `@WebMvcTest` + MockMvc | Paths, status codes and JSON shape match the contract |
| End to end | `@SpringBootTest(webEnvironment=RANDOM_PORT)` + Testcontainers | Seeded data, `PUT` then `GET` returns the expected balances |
| Architecture | ArchUnit | Domain and application never import Spring, JPA or adapter packages |

- One shared `@TestConfiguration` with a `PostgreSQLContainer` bean, reused across test classes so the container starts once per run.
- The same container config backs `TestTimeDepositApplication` (`SpringApplication.from(...)`) so `mvn spring-boot:test-run` starts the app locally without compose.
- Fast unit tests run in `test` (surefire); container tests run in `verify` (failsafe, `*IT`). The CI workflow already runs `mvn -B verify`, and GitHub's Ubuntu runners have Docker, so Testcontainers needs no CI change.

## Docker and compose

One image for deployment, one compose file for local runs; both live in `java/`.

**`java/Dockerfile`** (multi-stage):

1. Build stage on `maven:3-eclipse-temurin-25`: copy `pom.xml` first and resolve dependencies (cached layer), then copy `src` and run `mvn -B -DskipTests package`. Tests stay in CI, not in the image build, because container tests need Docker.
2. Extract the Boot jar with `java -Djarmode=tools -jar app.jar extract --layers --launcher` so dependencies and app code are separate layers.
3. Runtime stage on `eclipse-temurin:25-jre`: non-root user, `EXPOSE 8080`, `ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]`.
4. All connection settings come from env vars (`SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD`); nothing is baked in.

**`java/compose.yaml`**:

| Service | Image | Notes |
| --- | --- | --- |
| `postgres` | `postgres:18` | Volume for data, `pg_isready` healthcheck, port 5432 |
| `app` | built from `./Dockerfile` | `depends_on: postgres (service_healthy)`, `SPRING_PROFILES_ACTIVE=local` to load seed data, port 8080 |

- Local run: `docker compose up --build`, then open `http://localhost:8080/swagger-ui.html`.
- Dev loop alternative: `spring-boot-docker-compose` (test/optional dependency) starts only `postgres` from the same file when running the app from the IDE.
- `.dockerignore` excludes `target/`, `.idea/` and `*.iml`.

## Build quality: Spotless and Snyk

Formatting and dependency scanning run in the Maven build and in CI, so neither depends on an IDE setting.

**Spotless** (`spotless-maven-plugin`, latest stable, pinned):

- Java: `palantirJavaFormat`, `removeUnusedImports`, `importOrder`, `trimTrailingWhitespace`, `endWithNewline`. Also `pom.xml` via `sortPom`, plus YAML and SQL files (`compose.yaml`, OpenAPI contract, Flyway migrations) via their Spotless steps.
- `apply` goal bound to the `package` phase, so `mvn package` formats the sources for you.
- `check` goal bound to the `verify` phase, so unformatted code fails the build.
- Intent: auto-format locally, check only in the pipeline. Locally `apply` runs before `check`, so a local build never fails on formatting. CI runs `mvn -B verify -Dspotless.apply.skip=true`, so the pipeline never rewrites code and fails on unformatted files.
- Generated OpenAPI sources under `target/` are excluded.
- The first formatting commit lands on its own, so later diffs only show real changes.

**Snyk** (dependency vulnerability scan):

- CI job `snyk` in `.github/workflows/build.yml` using `snyk/actions/maven` on `java/`, after the build job, with `--severity-threshold=high` so high and critical issues fail the PR.
- Needs a `SNYK_TOKEN` repository secret. Without it, the job is skipped instead of failing, so forks and early PRs stay green.
- Also `snyk container test` on the built Docker image once the Dockerfile exists, to cover the JRE base image.
- Local run: `snyk test --file=java/pom.xml` with the Snyk CLI; documented in the README.
- Results upload as SARIF to GitHub code scanning, so findings show in the Security tab.

## Delivery steps

Each step is one or a few atomic commits that leave `mvn -B verify` green. Steps 1 to 3 need no Spring and could ship as their own PR.

1. Spotless plugin plus one formatting-only commit; Snyk CI job. Then characterization tests for `updateBalance` (all plans, day edges, rounding), run against the untouched code.
2. Introduce `InterestPolicy`, the three policies and the registry; switch `TimeDepositCalculator` to delegate. Characterization tests stay green unchanged.
3. Unit tests per policy and an ArchUnit rule for the package boundaries.
4. Add Spring Boot parent (latest 4.0.x, pinned exactly), web, data-jpa, actuator (off by default), validation, PostgreSQL driver, Flyway (`flyway-core` + `flyway-database-postgresql`), springdoc, Testcontainers; failsafe plugin. Add `TimeDepositApplication`.
5. Application layer: ports, `TimeDepositService`, read model; unit tests with an in-memory fake.
6. Flyway migrations V1, V2 and local seed data.
7. Persistence adapter: entities, Spring Data repositories, mapper; `@DataJpaTest` with Testcontainers.
8. OpenAPI contract + generator plugin; controller implementing the generated interface; `@WebMvcTest`.
9. End-to-end `@SpringBootTest` with Testcontainers; `TestTimeDepositApplication` for local runs.
10. `Dockerfile`, `.dockerignore`, `compose.yaml`; check `docker compose up` and both endpoints via Swagger UI.
11. README: how to run, how to call the endpoints, design notes and assumptions, AI-assisted development section (harness, AGENTS.md rules, what was AI-assisted). Update the project map in AGENTS.md.

## Decisions

Agreed with Daniel on 2026-11-07:

| Topic | Decision |
| --- | --- |
| Table names | snake_case (`time_deposits`, `withdrawals`) |
| Endpoints | `GET /api/v1/time-deposits`, `PUT /api/v1/time-deposits/balances`; URI versioning |
| Actuator | Disabled by default; enabled for `local` and `dev` profiles on a separate port |
| Base branch | Always the latest `main`; rule in AGENTS.md |
| `days` not advancing | Keep current behaviour; document it in the `updateBalance` Javadoc with a `TODO` |
| Package layout | One Maven module, hexagonal packages enforced by ArchUnit |
| Dependency scanning | Snyk |
| Formatting | Spotless: auto-format on `package` locally, `check` on `verify`; CI only checks |
