# Distributed Lock Rules

## Correctness and execution

- **LOCK-DEFENSE-001** **[REQUIRED][TOPIC]** — A distributed lock is never the sole correctness guarantee; handle expiry, pauses, partitions, and duplicate execution and use the independent correctness protection defined by the persistence concurrency rule.
- **LOCK-PORT-001** **[REQUIRED][TOPIC]** — Use the injected `LockExecutor` and `LockContext(waitTime, lockNames)`; business code must not depend directly on Redisson, Spring Integration Redis Lock, or raw Redis lock commands.
- **LOCK-KEY-STABILITY-001** **[REQUIRED][TOPIC]** — Business lock key segments together identify the same contended resource stably; never use random keys or multiple formats for one resource.
- **LOCK-KEY-RESOLUTION-001** **[REQUIRED][TOPIC]** — `LockExecutor` internally normalizes business segments through `ResourceNameResolver`, including `String.join` and namespace injection; callers pass business segments and never resolve or pre-prefix keys.
- **LOCK-KEY-SENSITIVE-001** **[REQUIRED][TOPIC]** — Lock key segments contain no sensitive data under the logging definition.
- **LOCK-WAIT-BOUND-001** **[REQUIRED][TOPIC]** — Bound wait time by business SLA; never wait indefinitely or spin without backoff.
- **LOCK-WAIT-IMMEDIATE-001** **[DEFAULT][TOPIC]** — Prefer immediate lock acquisition by default.
- **LOCK-WAIT-FAILURE-001** **[REQUIRED][TOPIC]** — On acquisition failure return an explicit retryable, conflict, or degradation outcome.
- **LOCK-LEASE-BOUND-001** **[REQUIRED][TOPIC]** — Cover the normal critical-section upper bound with a lease and safety margin; bound critical-section duration even with automatic renewal.
- **LOCK-CRITICAL-SECTION-001** **[REQUIRED][TOPIC]** — Restrict critical sections to necessary read/modify/write work, excluding avoidable remote calls, large batch processing, and long transactions.
- **LOCK-RELEASE-001** **[REQUIRED][TOPIC]** — Use `LockExecutor`-managed execution so only the owning flow unlocks in `finally`; do not transfer lock ownership across threads.
- **LOCK-TRANSACTION-BOUNDARY-001** **[REQUIRED][TOPIC]** — When a lock protects database work, acquire it before opening the protected transaction and release it only after commit or rollback completes. A method returning is not proof of transaction completion; verify actual boundaries with outer transactions and proxies.
- **LOCK-ORDER-001** **[REQUIRED][TOPIC]** — Define one global acquisition order for multiple locks; when one aggregate or stable business key can represent the resource, use that single lock instead of expanding the lock set.
