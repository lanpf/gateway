# Distributed Lock Rules

## Correctness and execution

- **LOCK-DEFENSE-001** — A distributed lock is never the sole correctness guarantee; handle expiry, pauses, partitions, and duplicate execution and use the independent correctness protection defined by the persistence concurrency rule.
- **LOCK-PORT-001** — Use the injected `LockExecutor` and `LockContext(waitTime, lockNames)`; business code must not depend directly on Redisson, Spring Integration Redis Lock, or raw Redis lock commands.
- **LOCK-KEY-001** — Pass stable business key segments as `lockNames` that together identify the contended resource; the lock starter resolves them internally through `ResourceNameResolver` (key-segment `String.join` and namespace injection), so callers must not resolve, assemble, or pre-prefix lock keys themselves.
- **LOCK-KEY-002** — `lockNames` must not contain sensitive data or random values, and the same resource must never appear under multiple key formats.
- **LOCK-WAIT-001** — Bound lock wait time by the use-case SLA, prefer immediate acquisition, and return an explicit retry/conflict/degradation result on failure; never wait indefinitely or spin without backoff.
- **LOCK-LEASE-001** — Configure a lease longer than the normal critical-section bound, keep critical sections short, and avoid remote calls, bulk work, and long transactions even when automatic renewal is enabled.
- **LOCK-RELEASE-001** — Use `LockExecutor`-managed execution so only the owning flow unlocks in `finally`; do not transfer lock ownership across threads.
