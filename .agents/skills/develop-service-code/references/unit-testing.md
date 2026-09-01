# Unit Testing Rules

## Phase and boundaries

- **TEST-PHASE-001** — During the development phase, write and run only unit tests and module-contract tests; the code is not yet stable, so do not write or run integration tests during development and never substitute them for unit tests.
- **TEST-LOCATION-001** — Keep unit and module contract tests in the tested module's `src/test`.
- **TEST-BEHAVIOR-001** — Verify observable behavior and public contracts rather than unnecessary implementation details.
- **TEST-MOCK-001** — In unit tests, mock only injected external collaborators; do not partially mock or spy on the subject under test.

## Structure and naming

- **TEST-REUSE-001** — Extract test support only for stable and meaningful reuse; do not create a common test base merely to remove small duplication.
- **TEST-UNIT-NAME-001** — Name unit and module contract test classes `*Test` and execute them with Maven Surefire in the test phase.
- **TEST-METHOD-NAME-001** — Name test methods `should<ExpectedBehavior>` to state the observable behavior directly.
