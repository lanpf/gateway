---
name: develop-service-code
description: Use when developing, changing, or reviewing service code under the shared engineering standards during development, including dependencies, layering, naming, Java language, common tools, logging, error codes, persistence, distributed IDs, and unit testing.
---

# Develop Service Code

## Workflow

1. Inspect the target module, nearby types, and existing public contracts.
2. Read `references/architecture.md` before creating or moving modules, packages, or configuration resources, and `references/dependency-rules.md` plus `references/service-baseline.md` when changing Maven dependencies or technology assembly.
3. Read `references/java-language.md`, `references/common-utilities.md`, and `references/constants-and-literals.md` for Java implementation and review.
4. Read `references/lombok.md`, `references/mapstruct.md`, and `references/logging.md` before changing Lombok annotations, object conversion, or log statements.
5. Read `references/validation-exceptions.md` when changing inputs, validation, or failures, and `references/error-codes.md` when defining or using error codes.
6. Read `references/naming.md` and `references/persistence-naming.md` before naming any new type, file, or query, and `references/resource-naming.md` before defining cache keys, resource keys, or namespaced configuration.
7. Read `references/api-domain-application.md`, `references/infrastructure-interfaces-boot.md`, and `references/data-carriers.md` before changing layer responsibilities or data carriers.
8. Read `references/persistence.md` before changing data access, transactions, or queries, and `references/distributed-id.md` before generating identifiers.
9. Write and run unit tests per `references/unit-testing.md`; integration tests belong to the smoke-test phase and are covered by `$test-integration`.
10. Preserve compatibility unless the task explicitly authorizes a breaking change.
11. Compile and run the narrowest relevant tests.

Prefer explicit, readable business logic. Treat every `required` rule as a release-blocking constraint.
