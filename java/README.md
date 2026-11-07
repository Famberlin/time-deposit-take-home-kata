# Time Deposit service (Java)

Spring Boot service for XA Bank time deposits: lists deposits and applies monthly interest. Design and decisions: [implementation plan](../docs/java-implementation-plan.md).

> Status: in progress. Sections below describe the target setup from the plan.

## Requirements

- JDK 25, Maven 3.9+
- Docker (for compose and Testcontainers)

## Run locally

```bash
cd java
docker compose up --build
```

Then open Swagger UI at <http://localhost:8080/swagger-ui.html>. The `local` profile loads demo data.

## API

Base path `/api/v1`. Contract: [`src/main/resources/openapi/time-deposits.yaml`](src/main/resources/openapi/time-deposits.yaml).

| Method | Path | Does |
| --- | --- | --- |
| `GET` | `/time-deposits` | All deposits with withdrawals, as `{ "items": [...], "total": n }` |
| `PUT` | `/time-deposits/balances` | Adds one month of interest to every balance; returns `204` |

```bash
curl http://localhost:8080/api/v1/time-deposits
curl -X PUT http://localhost:8080/api/v1/time-deposits/balances
```

Interest per month: basic 1%, student 3% (none after 1 year), premium 5% (from day 46); no plan earns interest in the first 30 days.

## Build and test

```bash
mvn package    # build; also auto-formats with Spotless
mvn verify     # unit + Testcontainers integration tests + Spotless check
```

## Database

PostgreSQL, schema managed by Flyway: [`src/main/resources/db/migration`](src/main/resources/db/migration).

## Configuration

| Variable | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | Database connection |
| `SPRING_PROFILES_ACTIVE` | `local` loads demo data; `local` and `dev` enable actuator (`health`, `info` on port 8081) |
