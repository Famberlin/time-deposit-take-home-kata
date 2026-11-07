# Agent rules

Rules for any coding agent working in this repo. Task requirements live in `README.md`.

## Scope

- Work only in `java/` and `kotlin/` (Maven, JDK 25).
- Ignore `c#/`, `python/` and `typescript/`. They are excluded in `.gitignore`: do not read, search, build or edit them, and leave them out of greps (`grep -r --exclude-dir={c#,python,typescript,target,.idea}`).
- Never read `target/`, `.idea/` or `*.iml`.

## Project map

```
README.md                         assignment and requirements
docs/java-implementation-plan.md  agreed plan and decisions for the Java solution
java/README.md                    how to run, call and build the Java service
java/pom.xml                      JUnit 6, AssertJ
java/src/main/java/org/ikigaidigital/
  TimeDeposit.java                shared model; do not break its API
  TimeDepositCalculator.java      updateBalance; keep signature and behaviour
java/src/test/java/org/ikigaidigital/TimeDepositCalculatorTest.java
kotlin/pom.xml                    Kotlin 2.4, JUnit 6, AssertJ
kotlin/src/main/kotlin/org/ikigaidigital/
  TimeDeposit.kt
  TimeDepositCalculator.kt
kotlin/src/test/kotlin/org/ikigaidigital/TimeDepositCalculatorTest.kt
```

## Keep context small

- Never let build or test output stream into the conversation. Send it to a file, then read only the summary and failures:

  ```bash
  cd java   # or kotlin
  mkdir -p target && mvn -B -q test > target/build.log 2>&1; echo "exit=$?"
  grep -E "Tests run:|FAIL|ERROR|BUILD" target/build.log | tail -20
  ```

- On failure, read the matching `target/surefire-reports/*.txt` or a narrow slice of `target/build.log` (`grep -n -A15 "<TestName>"`), never the whole log.
- Run one test with `-Dtest=ClassName#method` instead of the full suite while iterating.
- Read files by line range once you know where to look; do not re-read files you already have.
- Keep replies short: state the result and the failing test or error line, not the full output.

## Working rules

- Before starting any task, fetch `origin/main` and branch from the latest `main` (`git fetch origin main && git checkout -b <branch> origin/main`). Earlier work is merged there; never build on a stale feature branch. If your branch falls behind, rebase it onto `origin/main`.
- Keep `TimeDepositCalculator.updateBalance` behaviour unchanged; run its tests before and after refactoring.
- Small, atomic commits with clear messages.
