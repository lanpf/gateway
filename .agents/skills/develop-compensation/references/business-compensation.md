# Business Compensation and Fallback Rules

## Compensation design and operation

- **SERVICE-SCHEDULER-001** **[REQUIRED][TOPIC]** — Use compensation workflows only for fallback, compensation, reconciliation, repair, cleanup, and batch work; core real-time correctness must not depend on timely compensation execution.
- **JOB-BOUNDARY-001** **[REQUIRED][TOPIC]** — Keep job, message, startup-recovery, and manual triggers as thin entry adapters that delegate to application use cases or shared infrastructure recovery services; trigger adapters contain no domain rules.
- **JOB-IDEMPOTENCY-001** **[REQUIRED][TOPIC]** — Jobs must be reentrant, retryable, and idempotent rather than assuming one trigger; data-writing effects follow the persistence concurrency rule.
- **COMPENSATION-CONCURRENCY-001** **[REQUIRED][TOPIC]** — When one resource needs a single processing flow, converge execution through stable mutually exclusive ownership; a shard key must map stably to the resource, use a distributed lock when sharding alone is not exclusive, and retain independent idempotency or concurrency protection for writes.
- **JOB-BATCH-BOUND-001** **[REQUIRED][TOPIC]** — Use bounded pages or shards with stable sorted cursors, bounded batch size, and bounded runtime; never load all data at once.
- **JOB-BATCH-TRANSACTION-001** **[REQUIRED][TOPIC]** — Never hold a transaction across multiple batches.
- **JOB-CHECKPOINT-001** **[REQUIRED][TOPIC]** — Persist a recoverable checkpoint after each committed batch and resume from a recorded committed boundary. If a batch commits before its checkpoint is saved, replay must remain safe without amplifying side effects.
- **COMPENSATION-PARAMETER-VALIDITY-001** **[REQUIRED][TOPIC]** — Parameters carry only validated business scope, cursors, or switches and fail fast when missing or invalid.
- **COMPENSATION-PARAMETER-SECRET-001** **[REQUIRED][TOPIC]** — Job parameters must not contain secrets.
- **JOB-OPERATIONS-001** **[REQUIRED][TOPIC]** — Configure execution timeouts, alerts, log retention, and failure handling.
- **JOB-LOG-CONTEXT-001** **[REQUIRED][TOPIC]** — Log job name, executor, shard, batch, processed count, and outcome; follow logging rules for sensitive data.

## XXL-JOB trigger adapter

- **COMPENSATION-XXLJOB-BOUNDARY-001** **[REQUIRED][TOPIC]** — An XXL-JOB scheduler implementation module provides only the trigger entry and framework adaptation and contains no compensation rule or use-case orchestration.
- **COMPENSATION-XXLJOB-CONFIG-001** **[REQUIRED][TOPIC]** — Supply XXL-JOB admin address, namespace, port, access token, and enablement through environment configuration; production must not depend on starter defaults for critical connectivity.
