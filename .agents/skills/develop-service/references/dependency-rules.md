# Dependency Rules

## Module dependencies

- **DEP-MINIMAL-001** **[REQUIRED][BASELINE]** — Each module declares only the minimum dependencies required by its current compile-time and runtime semantics.
- **DEP-CYCLE-002** **[REQUIRED][BASELINE]** — Module cycles are forbidden; remove them by extracting a shared module or correcting responsibility boundaries, never by hiding the cycle with reflection or events.
- **DEP-SERVICE-MODULE-MANAGEMENT-001** **[REQUIRED][BASELINE]** — The service parent POM must declare every service module except test-only modules and the boot assembly module under `<dependencyManagement><dependencies>`, using `${project.version}`. Do not declare those modules as direct parent-POM dependencies, because direct dependencies are inherited by child modules and would violate module dependency boundaries.
- **DEP-STARTER-001** **[REQUIRED][BASELINE]** — Only concrete technical implementations, runtime adapters, and final assembly modules may depend on aggregate starters; other modules use the smallest capability artifact.

## Scopes and versions

- **DEP-ADMISSION-REVIEW-001** **[REQUIRED][BASELINE]** — Before adding dependencies, assess maintenance, security, license, and dependency cost.
- **DEP-OVERLAP-001** **[REQUIRED][BASELINE]** — Do not add functionally overlapping dependencies when existing dependencies meet the need.
- **DEP-SCOPE-USAGE-001** **[REQUIRED][BASELINE]** — Choose scope by actual use: compile for compilation requirements, test for tests, and provided only for capabilities genuinely supplied by the runtime.
- **DEP-OPTIONAL-001** **[REQUIRED][BASELINE]** — Use optional only for a genuinely optional capability; it is not a replacement for choosing the correct scope.
- **DEP-SNAPSHOT-001** **[REQUIRED][BASELINE]** — SNAPSHOT dependencies are forbidden in release branches and production builds and may only be temporary in local or unpublished iteration branches.
- **DEP-VERSION-001** **[REQUIRED][BASELINE]** — Manage third-party versions centrally; business and feature modules must not override versions independently.
