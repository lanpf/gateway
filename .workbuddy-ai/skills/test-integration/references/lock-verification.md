# Distributed Lock Verification Rules

## Lock integration

- **TEST-LOCK-INTEGRATION-001** **[REQUIRED][TOPIC]** — Use real Redis in the smoke-test phase to verify exclusion, acquisition failure, exceptional release, lease or renewal, and namespace isolation.
- **TEST-LOCK-CORRECTNESS-001** **[REQUIRED][TOPIC]** — In a concurrent-write smoke test, prove both that one business result takes effect and that a database uniqueness constraint blocks a duplicate that bypasses the lock.
