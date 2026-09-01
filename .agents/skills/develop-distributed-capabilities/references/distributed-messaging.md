# Distributed Messaging Rules

## Publication and ordering

- **MESSAGE-OUTBOX-001** — Application submits integration events only through `IntegrationEventOutbox`; persist state and outbox in one local transaction and signal publication only after commit.
- **MESSAGE-CONTRACT-001** — Integration events are stable service contracts with unique event ID, event type, aggregate type, aggregate ID, and occurrence time; never serialize domain entities directly.
- **MESSAGE-BATCH-001** — An outbox batch contains events from one aggregate and preserves batch sequence; split cross-aggregate events and never promise global ordering.
- **MESSAGE-PARTITION-001** — When aggregate ordering is required, use its stable ID as the partition key; do not use random or mutable keys and do not depend on global ordering.
- **MESSAGE-EVOLUTION-001** — Evolve event contracts backward-compatibly; new fields need compatible defaults, while deletion, rename, or semantic changes require a versioned migration.

## Consumption and delay

- **MESSAGE-IDEMPOTENCY-001** — Design consumers for at-least-once delivery and deduplicate by event ID or a stable business idempotency key; never assume a broker, outbox, or listener delivers once.
- **MESSAGE-ACK-001** — Acknowledge only after business processing and the local transaction succeed; use classified bounded retry/backoff and route exhausted or non-recoverable messages to dead-letter or manual handling.
- **MESSAGE-LISTENER-001** — Listeners deserialize, validate, enter idempotency control, and must not swallow failures before acknowledgement; their layer responsibility follows the interfaces rules.
- **MESSAGE-DELAY-001** — Use delayed messages only for error-tolerant retry, reminder, and auxiliary workflows, never as a precise timer or sole core-business deadline guarantee; consumers recheck state and idempotency on arrival.
- **MESSAGE-OBSERVABILITY-001** — Monitor publisher confirms/returns, outbox backlog, retries, consumer lag, and dead letters with event ID, aggregate ID, and destination trace context.
