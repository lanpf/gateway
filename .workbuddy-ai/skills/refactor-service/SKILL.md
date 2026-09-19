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
7. Before producing a plan, load `$develop-service`'s architecture and layer-responsibility references and proactively scan the requested refactor scope for responsibility leakage, forwarding-only layering, and dependency-direction violations using their semantic criteria. Trace actual entry-to-use-case-to-domain-to-adapter call chains and inspect POM and source dependencies; do not limit discovery to supplied rule IDs, names, packages, or the current diff. For a change-list-driven refactor, inspect the behavior and dependencies affected by that list rather than extending to an unrelated project-wide audit.
8. Produce a plan before modifying code. For every finding, state the rule IDs, evidence locations/call chain, current and intended owner, impacted modules/files, compatibility considerations, decision and test migration, dependency corrections, and verification commands. Explain why suspected technical adapter behavior is not a violation when relevant. Record pre-existing findings outside the requested scope separately with impact; do not silently add them to implementation. The plan must resolve in-scope required-rule violations or identify blocking work needed beyond scope.
9. After explicit user confirmation, apply only the approved plan, preserve unrelated changes, run the planned verification, and repeat the responsibility and dependency scan over the affected call chains. Recheck the baseline checklist and loaded topic rules. Report each planned finding's resolution with evidence and any residual findings; unresolved in-scope required-rule violations block completion, and default-rule deviations need concrete reported reasons. Do not treat class moves, forwarding layers, or a successful build alone as proof that responsibilities have been corrected.
10. Commit only when separately authorized or when an invoking workflow explicitly authorizes it.
