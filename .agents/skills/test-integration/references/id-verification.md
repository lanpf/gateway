# Distributed ID Verification Rules

## Generator integration

- **TEST-ID-GENERATOR-001** **[REQUIRED][TOPIC]** — In the smoke-test phase, verify consecutive generation, isolation between generator names, and fast failure for a missing name.
- **TEST-ID-MULTI-INSTANCE-001** **[REQUIRED][TOPIC]** — For multi-instance deployment, verify that node identities do not conflict and clock rollback follows configured policy.
