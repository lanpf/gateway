---
name: develop-compensation
description: Use when designing, implementing, or reviewing business compensation, reconciliation, repair, cleanup, batch recovery, or fallback workflows, regardless of whether they are triggered by jobs, messages, startup recovery, or manual operations.
---

# Develop Compensation Workflows

## Enforcement vocabulary

`required` rules are release-blocking. `default` rules apply unless a concrete deviation reason is recorded. `advisory` rules are optional guidance. `baseline` rules are preloaded for every change; `topic` rules are loaded when the affected capability matches.

## Workflow

1. Use `$develop-service` first to load and enforce the baseline rules for every code change.
2. Identify the business invariant being restored, the recovery state, the authoritative data source, and the acceptable recovery delay.
3. Read `references/business-compensation.md` completely before designing or changing the workflow.
4. Keep the compensation workflow independent of its trigger; treat XXL-JOB, messaging, startup recovery, and manual operations as replaceable entry adapters.
5. Keep every entry adapter thin and delegate to an application use case or reusable infrastructure recovery service.
6. Make processing idempotent, reentrant, bounded, observable, and resumable from committed checkpoints.
7. During development, test duplicate triggers, partial failure and resume, concurrent execution, empty input, invalid parameters, and timeout behavior with unit or module-contract tests.
8. Run the affected module build; record real-runtime scenarios and use `$test-integration` to implement and execute them only in the smoke-test phase.
9. Recheck the final diff against the `$develop-service` baseline checklist and the loaded compensation rules before completion.
