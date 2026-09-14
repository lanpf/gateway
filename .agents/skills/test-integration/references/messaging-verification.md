# Messaging Verification Rules

## Broker integration and recovery

- **TEST-MESSAGE-INTEGRATION-001** **[REQUIRED][TOPIC]** — Use a real broker in the smoke-test phase to cover post-commit publication, publication retry, duplicate delivery, aggregate ordering, dead letters, and delayed-delivery tolerance.
- **TEST-MESSAGE-RECOVERY-001** **[REQUIRED][TOPIC]** — Exercise database-commit/broker-unavailable, interrupted-consumer, and backlog-recovery failures in the smoke-test phase.
