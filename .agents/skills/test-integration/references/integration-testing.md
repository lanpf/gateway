# Integration Testing Rules

## Phase and boundaries

- **TEST-PHASE-002** **[REQUIRED][TOPIC]** — Write and execute integration tests only in the smoke-test phase to verify cross-module collaboration and real infrastructure behavior after code stabilization; development-phase testing follows the unit-test rules.
- **TEST-INTEGRATION-CLASSIFICATION-001** **[REQUIRED][TOPIC]** — Tests of real implementation collaboration across modules, full auto-configuration, real databases, or real middleware are integration tests; merely referencing another module's interface or DTO is insufficient.
- **TEST-INTEGRATION-PLACEMENT-001** **[REQUIRED][TOPIC]** — Put service integration tests in the architecture-defined standalone test module, never in a production module.
- **TEST-CONTAINERS-001** **[DEFAULT][TOPIC]** — Prefer Testcontainers for isolated and repeatable real-infrastructure scenarios.
- **TEST-CONTAINER-IMAGE-001** **[DEFAULT][TOPIC]** — When integration tests use containers, prefer an officially maintained lightweight image variant when its functionality, version, and target architecture satisfy the test; use the standard image only when the lightweight variant lacks required tools or capabilities.
- **TEST-CONTAINER-ARCH-001** **[REQUIRED][TOPIC]** — Confirm that each image manifest supports the target architectures of local development and CI runners.
- **TEST-CONTAINER-MULTIARCH-001** **[DEFAULT][TOPIC]** — Prefer one multi-architecture image that supports both local development and CI runners.

## Structure, documentation, and observation

- **TEST-SELF-CONTAINED-001** **[DEFAULT][TOPIC]** — Prefer self-contained integration scenarios, with containers, test application, configuration, probes, and lifecycle management in the corresponding `*IT`.
- **TEST-IT-CLASS-NAME-001** **[REQUIRED][TOPIC]** — Name integration tests `*IT`; method naming follows unit-test naming rules.
- **TEST-IT-RUNNER-001** **[REQUIRED][TOPIC]** — Run integration tests with Maven Failsafe in integration-test and verify phases.
- **TEST-CLEANUP-001** **[DEFAULT][TOPIC]** — Prefer `@Testcontainers` and `@Container` to manage external infrastructure.
- **TEST-RESOURCE-CLEANUP-001** **[REQUIRED][TOPIC]** — Release application contexts, executors, containers, and other created resources in `@AfterEach` or an equivalent lifecycle boundary.
- **TEST-DOC-001** **[REQUIRED][TOPIC]** — Every integration test has same-named documentation covering its purpose, conditions, execution, manual observation steps, observation entry points, and expected result.
- **TEST-MODULE-README-001** **[REQUIRED][TOPIC]** — A standalone test module README contains only coverage, environment requirements, unified execution, report location, and documentation index.
- **TEST-SCENARIO-DOC-LOCATION-001** **[REQUIRED][TOPIC]** — Put detailed integration scenarios in the standalone test module's `docs/<TestClass>.md`.
- **TEST-OBSERVABLE-001** **[REQUIRED][TOPIC]** — Integration tests must allow manual observation of the key process and final result; automatic assertions or source reading alone are insufficient.
