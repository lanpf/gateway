# Persistence Verification Rules

## Database integration

- **TEST-PERSISTENCE-INTEGRATION-001** **[REQUIRED][TOPIC]** — Use a real database or Testcontainers in the smoke-test phase to cover schema constraints, naming strategy, transaction rollback, concurrency conflicts, stable pagination, and critical query plans.
- **TEST-PERSISTENCE-CONTRACT-001** **[REQUIRED][TOPIC]** — When one repository contract has alternative implementations, run the same contract tests against each implementation in the smoke-test phase and verify equivalent observable behavior.
