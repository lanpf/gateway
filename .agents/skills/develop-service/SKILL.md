---
name: develop-service
description: Use when developing, changing, or reviewing service code under the shared engineering standards during development, including dependencies, layering, naming, Java language, common tools, logging, error codes, persistence, distributed IDs, and unit testing.
---

# Develop Service Code

## Enforcement vocabulary

`required` rules are release-blocking. `default` rules apply unless a concrete deviation reason is recorded. `advisory` rules are optional guidance. `baseline` rules are preloaded for every change; `topic` rules are loaded when the affected capability matches.

## Workflow

1. Inspect the target module, nearby types, existing public contracts, and Git changes.
2. Before planning or editing, always read the baseline references: `references/dependency-rules.md`, `references/service-baseline.md`, `references/java-language.md`, `references/constants-and-literals.md`, `references/common-utilities.md`, `references/validation.md`, `references/lombok.md`, `references/logging.md`, `references/mapstruct.md`, `references/naming.md`, and `references/unit-testing.md`.
3. Build a baseline checklist from every applicable `[REQUIRED][BASELINE]` and `[DEFAULT][BASELINE]` rule. A required rule is release-blocking; a default rule applies unless the implementation records a concrete reason to deviate; an advisory rule is optional guidance.
4. Read `references/architecture.md` before creating or moving modules, packages, or configuration resources.
5. Read `references/service-validation.md` when changing API, Spring MVC, Facade, application, or domain validation and exceptions; read `references/error-codes.md` when defining or using error codes, `references/persistence-naming.md` before naming a persistence query or mapper resource, and `references/resource-naming.md` before defining cache keys, resource keys, or namespaced configuration.
6. Read `references/api-domain-application.md`, `references/infrastructure-interfaces-boot.md`, and `references/data-carriers.md` before changing layer responsibilities or cross-layer data carriers.
7. Read `references/persistence.md` before changing data access, transactions, or queries, and `references/distributed-id.md` before generating identifiers.
8. Read `references/service-calls.md` before changing an internal or external service call.
9. Read `references/project-documentation.md` before changing service responsibilities, cross-service collaboration contracts, delivery status statements, or the project documentation structure.
10. Write and run unit tests per `references/unit-testing.md`; integration tests belong to the smoke-test phase and are covered by `$test-integration`.
11. Preserve compatibility unless the task explicitly authorizes a breaking change.
12. Compile and run the narrowest relevant tests.
13. Before completion, inspect the final diff against the baseline checklist and every loaded topic rule. Report any justified default-rule deviation and do not complete while a required rule is violated.

Prefer explicit, readable business logic.
