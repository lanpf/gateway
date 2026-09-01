# Dependency Rules

## Module dependencies

- **DEP-MINIMAL-001** — Each module declares only the minimum dependencies required by its current compile-time and runtime semantics.
- **DEP-CYCLE-002** — Module cycles are forbidden; remove them by extracting a shared module or correcting responsibility boundaries, never by hiding the cycle with reflection or events.
- **DEP-SERVICE-MODULE-MANAGEMENT-001** — The service parent POM must declare every service module except test-only modules and the boot assembly module under `<dependencyManagement><dependencies>`, using `${project.version}`. Do not declare those modules as direct parent-POM dependencies, because direct dependencies are inherited by child modules and would violate module dependency boundaries.
- **DEP-STARTER-001** — Only concrete technical implementations, runtime adapters, and final assembly modules may depend on aggregate starters; other modules use the smallest capability artifact.

## Scopes and versions

- **DEP-SCOPE-001** — Match dependency scope to use: compile requirements use compile scope, tests use test scope, and `provided` or `optional` requires a genuine runtime-provided or optional capability.
- **DEP-SNAPSHOT-001** — SNAPSHOT dependencies are forbidden in release branches and production builds and may only be temporary in local or unpublished iteration branches.
- **DEP-VERSION-001** — Manage third-party versions centrally; business and feature modules must not override versions independently.
