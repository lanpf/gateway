# Distributed Messaging Rules

## Publication and ordering

- **MESSAGE-OUTBOX-001** **[REQUIRED][TOPIC]** — Application submits integration events only through `IntegrationEventOutbox`; persist state and outbox in one local transaction and signal publication only after commit.
- **MESSAGE-CONTRACT-001** **[REQUIRED][TOPIC]** — Integration events are stable service contracts with unique event ID, event type, aggregate type, aggregate ID, and occurrence time; never serialize domain entities directly.
- **MESSAGE-BATCH-001** **[REQUIRED][TOPIC]** — An outbox batch contains events from one aggregate and preserves batch sequence; split cross-aggregate events and never promise global ordering.
- **MESSAGE-PARTITION-001** **[REQUIRED][TOPIC]** — When aggregate ordering is required, use its stable ID as the partition key; do not use random or mutable keys and do not depend on global ordering.
- **MESSAGE-DESTINATION-CONFIG-001** **[REQUIRED][TOPIC]** — Configure and validate destinations, partition counts, weights, and routing algorithms centrally at startup; business code must not create unmanaged destinations dynamically.
- **MESSAGE-PARTITION-MIGRATION-001** **[REQUIRED][TOPIC]** — Before changing a partition count or routing algorithm, assess in-flight messages and ordering impact and define a migration plan.
- **MESSAGE-EVOLUTION-001** **[REQUIRED][TOPIC]** — Evolve event contracts backward-compatibly; new fields need compatible defaults, while deletion, rename, or semantic changes require a versioned migration.

## Consumption and delay

- **MESSAGE-IDEMPOTENCY-001** **[REQUIRED][TOPIC]** — Design consumers for at-least-once delivery and deduplicate by event ID or a stable business idempotency key; never assume a broker, outbox, or listener delivers once.
- **MESSAGE-IDEMPOTENCY-ATOMIC-001** **[REQUIRED][TOPIC]** — Atomically commit the completed-consumption deduplication record with local business writes in the same transaction; never mark a message processed before separate fallible writes. Nontransactional external effects require a recoverable idempotency protocol preventing lost processing or repeated side effects.
- **MESSAGE-ACK-001** **[REQUIRED][TOPIC]** — Acknowledge only after business processing and the local transaction succeed; use classified bounded retry/backoff and route exhausted or non-recoverable messages to dead-letter or manual handling.
- **MESSAGE-LISTENER-001** **[REQUIRED][TOPIC]** — Listeners deserialize, validate, enter idempotency control, and must not swallow failures before acknowledgement; their layer responsibility follows the interfaces rules.
- **MESSAGE-DELAY-001** **[REQUIRED][TOPIC]** — Use delayed messages only for error-tolerant retry, reminder, and auxiliary workflows, never as a precise timer or sole core-business deadline guarantee; consumers recheck state and idempotency on arrival.
- **MESSAGE-DELAY-PORT-001** **[REQUIRED][TOPIC]** — Use `DelayedOperations` with an explicit delay and keep RabbitMQ delayed-topology details out of business code.
- **MESSAGE-DELAY-CONFIG-001** **[REQUIRED][TOPIC]** — When at-least-once delayed delivery is required, use a quorum configuration that supports it and validate delay levels, tick, and maximum TTL at startup.
- **MESSAGE-OBSERVABILITY-001** **[REQUIRED][TOPIC]** — Monitor publisher confirms/returns, outbox backlog, retries, consumer lag, and dead letters with event ID, aggregate ID, and destination trace context.
