# Integration Testing Rules

## Phase and boundaries

- **TEST-PHASE-002** **[REQUIRED][TOPIC]** — Write and execute integration tests only in the smoke-test phase to verify cross-module collaboration and real infrastructure behavior after code stabilization; development-phase testing follows the unit-test rules.
- **TEST-INTEGRATION-001** **[REQUIRED][TOPIC]** — Treat collaboration among real implementations from multiple modules, full auto-configuration, real databases, or real middleware as integration tests and keep them out of production modules. Merely referencing another module's interface or DTO is not sufficient; service integration tests use the architecture-defined standalone module.
- **TEST-CONTAINERS-001** **[DEFAULT][TOPIC]** — Prefer Testcontainers for isolated and repeatable real-infrastructure scenarios.
- **TEST-CONTAINER-IMAGE-001** **[DEFAULT][TOPIC]** — When integration tests use containers, prefer an officially maintained lightweight image variant when its functionality, version, and target architecture satisfy the test; use the standard image only when the lightweight variant lacks required tools or capabilities.
- **TEST-CONTAINER-ARCH-001** **[REQUIRED][TOPIC]** — Confirm that each image manifest supports the target architectures of local development and CI runners.
- **TEST-CONTAINER-MULTIARCH-001** **[DEFAULT][TOPIC]** — Prefer one multi-architecture image that supports both local development and CI runners.

## Structure, documentation, and observation

- **TEST-SELF-CONTAINED-001** **[REQUIRED][TOPIC]** — Keep integration scenarios self-contained; place containers, test application, configuration, probes, and lifecycle management in the corresponding `*IT` when practical.
- **TEST-IT-NAME-001** **[REQUIRED][TOPIC]** — Name integration test classes `*IT` and execute them with Maven Failsafe in the integration-test and verify phases.
- **TEST-CLEANUP-001** **[DEFAULT][TOPIC]** — Prefer `@Testcontainers` and `@Container` to manage external infrastructure.
- **TEST-RESOURCE-CLEANUP-001** **[REQUIRED][TOPIC]** — Release application contexts, executors, containers, and other created resources in `@AfterEach` or an equivalent lifecycle boundary.
- **TEST-DOC-001** **[REQUIRED][TOPIC]** — Every integration test has same-named documentation covering its purpose, conditions, execution, manual observation steps, observation entry points, and expected result.
- **TEST-DOC-002** **[REQUIRED][TOPIC]** — The root README of a standalone test module contains only coverage scope, environment requirements, unified execution, report location, and a documentation index; detailed scenarios live in `docs/<TestClass>.md`.
- **TEST-OBSERVABLE-001** **[REQUIRED][TOPIC]** — Integration tests must allow manual observation of the key process and final result; automatic assertions or source reading alone are insufficient.
