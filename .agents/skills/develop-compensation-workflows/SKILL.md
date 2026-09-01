---
name: develop-compensation-workflows
description: Use when designing, implementing, or reviewing business compensation, reconciliation, repair, cleanup, batch recovery, or fallback workflows, regardless of whether they are triggered by jobs, messages, startup recovery, or manual operations.
---

# Develop Compensation Workflows

## Workflow

1. Identify the business invariant being restored, the recovery state, the authoritative data source, and the acceptable recovery delay.
2. Read `references/business-compensation.md` completely before designing or changing the workflow.
3. Keep the compensation workflow independent of its trigger; treat XXL-JOB, messaging, startup recovery, and manual operations as replaceable entry adapters.
4. Keep every entry adapter thin and delegate to an application use case or reusable infrastructure recovery service.
5. Make processing idempotent, reentrant, bounded, observable, and resumable from committed checkpoints.
6. Test duplicate triggers, partial failure and resume, concurrent execution, empty input, invalid parameters, and timeout behavior.
7. Run the affected module build and the available integration verification for the selected trigger technology.
