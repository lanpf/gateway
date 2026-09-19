# Compensation Verification Rules

## Compensation and runtime

- **TEST-COMPENSATION-BEHAVIOR-001** **[REQUIRED][TOPIC]** — In the smoke-test phase, cover duplicate triggers, recovery after a partial-batch failure, concurrent instances, empty data, and invalid parameters.
- **TEST-COMPENSATION-RUNTIME-001** **[REQUIRED][TOPIC]** — When an actual trigger technology such as XXL-JOB is used, verify executor registration, namespace isolation, timeout, and failure alerts in the smoke-test phase.
