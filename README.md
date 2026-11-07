# Time Deposit Refactoring Kata - Take-Home Assignment

## XA Bank Time Deposit

### Context
A junior developer implemented domain logic for a time deposit system but did not complete the API functionality. Your task is to refactor the existing codebase to implement all required functionalities based on the provided business requirements, ensuring no breaking changes occur.

### Requirements

1. **API Endpoints**:
    - Create a RESTful API endpoint to update the balances of all time deposits in the database.
    - Create a RESTful API endpoint to retrieve all time deposits.
        - The GET endpoint should return a list of all time deposits with the following schema:
            - `id`
            - `planType`
            - `balance`
            - `days`
            - `withdrawals`

2. **Database Setup**:
    - Store all time deposit plans in a database.
    - Define the following tables:
        - `timeDeposits`:
            - `id`: Integer (primary key)
            - `planType`: String (required)
            - `days`: Integer (required)
            - `balance`: Decimal (required)
        - `withdrawals`:
            - `id`: Integer (primary key)
            - `timeDepositId`: Integer (foreign key, required)
            - `amount`: Decimal (required)
            - `date`: Date (required)

3. **Interest Calculation**:
    - Implement logic to calculate monthly interest based on the plan type:
        - **Basic Plan**: 1% interest
        - **Student Plan**: 3% interest (no interest after 1 year)
        - **Premium Plan**: 5% interest (interest starts after 45 days)
    - No interest is applied for the first 30 days for any existing plans.

4. **Refactoring Constraints**:
    - Do not introduce breaking changes to the shared `TimeDeposit` class or modify the `updateBalance` method signature.
    - Ensure the design is extensible to accommodate future complexities in interest calculations.

5. **Code Quality**:
    - Adhere to SOLID principles, design patterns, and clean code practices where applicable.

6. **AI-Assisted Development**:
    - Set up an AI harness or agent workflow and use it throughout the development for this take-home exercise.
    - Briefly document the tools and setup used (e.g., LLMs, coding assistants, agentic frameworks, configuration).
    - Ensure your AI setup is practical and reproducible.
    - Include any custom rules, system prompts, or agent configurations used.
    - Include a brief summary of which parts of the solution were AI-assisted and why.

### Important Guidelines
- The existing `TimeDepositCalculator.updateBalance` method is functioning correctly. Ensure its behavior remains unchanged after refactoring.
- The final solution must include **exactly two API endpoints**. Do not develop additional endpoints.
- **Do not** create a pull request or a new branch in the ikigai-digital repository. Instead, fork the repository into your own GitHub repository and develop the solution there.
- Handling invalid input or exceptions is not required.
- Use any tools, frameworks, or libraries you find suitable.
- In case of ambiguity, make logical assumptions and justify them in code comments.

### Preferred Stack
- Use an OpenAPI Swagger contract.
- Embrace Hexagonal Architecture.
- Follow atomic commit practices.
- Utilize testcontainers.
- Leverage AI-assisted development tools for code generation, testing, and refactoring.

### Submission Instructions
- Provide clear instructions on how to trigger the endpoints using the Swagger contract.
- Email the link to your public GitHub repository.
## Dependency and Tooling Upgrades

Before starting the refactoring, the `java/` and `kotlin/` projects were moved to current, supported versions so the solution builds on a modern toolchain. Behaviour of `TimeDepositCalculator.updateBalance` is unchanged; the existing tests pass before and after.

### Java (`java/pom.xml`)

| Item | Before | After | Notes |
|------|--------|-------|-------|
| JDK target | 17 (`source`/`target`) | 25 (`maven.compiler.release`) | Java 25 is the latest LTS. `release` also checks API usage against the target JDK. |
| maven-compiler-plugin | 3.8.1 | 3.16.0 | Latest stable 3.x (4.x is still beta). Duplicate `source`/`target` configuration removed. |
| maven-surefire-plugin | 3.0.0-M3 | 3.6.1 | Milestone replaced by a stable release. |
| junit-jupiter-engine | 5.9.0 | 6.1.3 | JUnit 6 requires Java 17+. |
| assertj-core | 3.23.1 | 3.27.7 | Latest stable 3.x (4.x is still a milestone). |

### Kotlin (`kotlin/pom.xml`)

| Item | Before | After | Notes |
|------|--------|-------|-------|
| Kotlin (plugin, stdlib, kotlin-test) | 1.7.20 | 2.4.21 | Latest stable; uses the K2 compiler. Version now set once in the `kotlin.version` property. |
| `kotlin-stdlib-jdk8` | 1.7.20 | replaced by `kotlin-stdlib` | The `-jdk7`/`-jdk8` variants were merged into `kotlin-stdlib` in Kotlin 1.8. |
| JVM target | 17 | 25 | `kotlin.compiler.jvmTarget` and `maven.compiler.release`. |
| maven-surefire-plugin | 2.22.2 | 3.6.1 | |
| exec-maven-plugin | 1.6.0 | 3.6.4 | |
| junit-bom | 5.9.1 | 6.1.3 | |
| assertj-core | 3.23.1 | 3.27.7 | |

### Continuous integration

`.github/workflows/build.yml` builds and tests both projects (`mvn -B verify`) on Temurin JDK 25 for every push to `main` and every pull request.
