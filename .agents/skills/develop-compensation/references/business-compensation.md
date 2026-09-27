# Business Compensation and Fallback Rules

## Compensation design and operation

- **SERVICE-SCHEDULER-001** **[REQUIRED][TOPIC]** — Use compensation workflows only for fallback, compensation, reconciliation, repair, cleanup, and batch work; core real-time correctness must not depend on timely compensation execution.
- **JOB-BOUNDARY-001** **[REQUIRED][TOPIC]** — Keep job, message, startup-recovery, and manual triggers as thin entry adapters that delegate to application use cases or shared infrastructure recovery services; trigger adapters contain no domain rules.
- **JOB-IDEMPOTENCY-001** **[REQUIRED][TOPIC]** — Jobs must be reentrant, retryable, and idempotent rather than assuming one trigger; data-writing effects follow the persistence concurrency rule.
- **COMPENSATION-CONCURRENCY-001** **[REQUIRED][TOPIC]** — When one resource needs a single processing flow, converge execution through stable mutually exclusive ownership; a shard key must map stably to the resource, use a distributed lock when sharding alone is not exclusive, and retain independent idempotency or concurrency protection for writes.
- **JOB-BATCH-001** **[REQUIRED][TOPIC]** — Process bounded pages or shards with a stable cursor and bounded runtime; do not load all data or hold a transaction across batches.
- **JOB-CHECKPOINT-001** **[REQUIRED][TOPIC]** — Persist a recoverable checkpoint after each committed batch and resume from a recorded committed boundary. If a batch commits before its checkpoint is saved, replay must remain safe without amplifying side effects.
- **COMPENSATION-PARAMETER-001** **[REQUIRED][TOPIC]** — Limit compensation parameters to validated business scope, cursors, and switches; never include a secret, and fail fast on a missing or invalid parameter.
- **JOB-OBSERVABILITY-001** **[REQUIRED][TOPIC]** — Configure timeouts, alerts, log retention, and failure handling; log job, executor, shard, batch, count, and result context without sensitive data.

## XXL-JOB trigger adapter

- **COMPENSATION-XXLJOB-BOUNDARY-001** **[REQUIRED][TOPIC]** — An XXL-JOB scheduler implementation module provides only the trigger entry and framework adaptation and contains no compensation rule or use-case orchestration.
- **COMPENSATION-XXLJOB-CONFIG-001** **[REQUIRED][TOPIC]** — Supply XXL-JOB admin address, namespace, port, access token, and enablement through environment configuration; production must not depend on starter defaults for critical connectivity.
