---
name: refactor-layered-service
description: Plan and apply coordinated standards-driven refactors across layered service architecture, Java code, dependencies, distributed capabilities, compensation workflows, persistence, error codes, and tests.
---

# Refactor Layered Service

## Workflow

1. Read the project's `AGENTS.md`, root `README.md`, and every authoritative document routed for the affected area.
2. Inspect Git status and record unrelated pre-existing changes before editing.
3. Use `$develop-service-code` for dependencies, layering, naming, Java, common tools, logging, error codes, persistence, distributed identifiers, and unit tests.
4. Use `$develop-distributed-capabilities` for locking, event publication, and message consumption.
5. Use `$develop-compensation-workflows` for compensation, reconciliation, repair, cleanup, batch recovery, or fallback flows.
6. Use `$test-integration` only for integration-test work in the smoke-test phase; keep development-phase verification in `$develop-service-code` unit tests.
7. Produce a plan before modifying code. For every item, identify the triggering rule or requested objective, impacted modules/files, compatibility considerations, implementation approach, and verification commands.
8. After explicit user confirmation, apply only the approved plan, preserve unrelated changes, run the planned verification, and report results. Commit only when separately authorized or when an invoking workflow explicitly authorizes it.
