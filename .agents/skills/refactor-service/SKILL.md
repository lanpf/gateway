---
name: refactor-service
description: Plan and apply coordinated standards-driven refactors across layered service architecture, Java code, dependencies, distributed capabilities, compensation workflows, persistence, error codes, and tests.
---

# Refactor Service

## Enforcement vocabulary

`required` rules are release-blocking. `default` rules apply unless a concrete deviation reason is recorded. `advisory` rules are optional guidance. `baseline` rules are preloaded for every change; `topic` rules are loaded when the affected capability matches.

## Workflow

1. Read the project's `AGENTS.md`, root `README.md`, and every authoritative document routed for the affected area.
2. Inspect Git status and record unrelated pre-existing changes before editing.
3. Use `$develop-service` first to load the complete baseline rule set and build the required/default baseline checklist, then use its topic references for dependencies, layering, package structure, naming, Java, common tools, logging, error codes, persistence, distributed identifiers, and unit tests.
4. Use `$develop-distributed` for locking, event publication, and message consumption.
5. Use `$develop-compensation` for compensation, reconciliation, repair, cleanup, batch recovery, or fallback flows.
6. Use `$test-integration` only for integration-test work in the smoke-test phase; keep development-phase verification in `$develop-service` unit tests.
7. Produce a plan before modifying code. For every item, identify the triggering rule or requested objective, impacted modules/files, compatibility considerations, implementation approach, verification commands, and affected baseline checklist entries.
8. After explicit user confirmation, apply only the approved plan, preserve unrelated changes, run the planned verification, and recheck the final diff against the baseline checklist and loaded topic rules. Required-rule violations block completion; default-rule deviations must be reported with their concrete reason.
9. Commit only when separately authorized or when an invoking workflow explicitly authorizes it.
