# Integration Testing Rules

## Phase and boundaries

- **TEST-PHASE-002** — Write and execute integration tests only in the smoke-test phase to verify cross-module collaboration and real infrastructure behavior after the code has stabilized; do not use integration tests as a substitute for unit tests during development.
- **TEST-INTEGRATION-001** — Treat cross-module, full auto-configuration, real database, or real middleware scenarios as integration tests; in a service, place them in the architecture-defined `<service>-integration-tests` module and keep them out of production modules.
- **TEST-CONTAINERS-001** — Prefer Testcontainers for isolated and repeatable real-infrastructure scenarios.
- **TEST-CONTAINER-IMAGE-001** — When integration tests use containers, prefer an officially maintained lightweight image variant when its functionality, version, and target architecture satisfy the test; use the standard image only when the lightweight variant lacks required tools or capabilities.
- **TEST-CONTAINER-ARCH-001** — Confirm that each image manifest supports the target architectures of local development and CI runners, and prefer multi-architecture images.

## Structure, documentation, and observation

- **TEST-SELF-CONTAINED-001** — Keep integration scenarios self-contained; place containers, test application, configuration, probes, and lifecycle management in the corresponding `*IT` when practical.
- **TEST-IT-NAME-001** — Name integration test classes `*IT` and execute them with Maven Failsafe in the integration-test and verify phases.
- **TEST-CLEANUP-001** — Manage external infrastructure with `@Testcontainers` and `@Container` where possible, and release created application contexts and executors in `@AfterEach`.
- **TEST-DOC-001** — Every integration test has same-named documentation covering its purpose, conditions, execution, manual observation steps, observation entry points, and expected result.
- **TEST-DOC-002** — The root README of a standalone test module contains only coverage scope, environment requirements, unified execution, report location, and a documentation index; detailed scenarios live in `docs/<TestClass>.md`.
- **TEST-OBSERVABLE-001** — Integration tests must allow manual observation of the key process and final result; automatic assertions or source reading alone are insufficient.
