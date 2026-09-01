# Business Compensation and Fallback Rules

## Compensation design and operation

- **SERVICE-SCHEDULER-001** — Use compensation workflows only for fallback, compensation, reconciliation, repair, cleanup, and batch work; core real-time correctness must not depend on timely compensation execution.
- **JOB-BOUNDARY-001** — Keep job, message, startup-recovery, and manual triggers as thin entry adapters that delegate to application use cases or shared infrastructure recovery services; trigger adapters contain no domain rules.
- **JOB-IDEMPOTENCY-001** — Jobs must be reentrant, retryable, and idempotent rather than assuming one trigger; data-writing effects follow the persistence concurrency rule.
- **JOB-BATCH-001** — Process bounded pages or shards with a stable cursor and bounded runtime; do not load all data or hold a transaction across batches.
- **JOB-CHECKPOINT-001** — Persist a recoverable checkpoint after each committed batch so retries resume at a committed boundary without amplifying side effects.
- **JOB-OBSERVABILITY-001** — Configure timeouts, alerts, log retention, and failure handling; log job, executor, shard, batch, count, and result context without sensitive data.
