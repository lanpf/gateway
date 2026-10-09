# Distributed Messaging Rules

## Publication and ordering

- **MESSAGE-OUTBOX-PORT-001** **[REQUIRED][TOPIC]** — Application submits integration events only through `IntegrationEventOutbox`, without direct RabbitMQ templates or broker APIs.
- **MESSAGE-OUTBOX-TRANSACTION-001** **[REQUIRED][TOPIC]** — Commit business state and outbox rows atomically in the same local transaction; direct publication must not replace reliable outbox semantics.
- **MESSAGE-OUTBOX-SIGNAL-001** **[REQUIRED][TOPIC]** — Trigger an outbox publication signal only after transaction commit.
- **MESSAGE-CONTRACT-FIELDS-001** **[REQUIRED][TOPIC]** — A stable integration-event contract includes globally unique event ID, event type, aggregate type, aggregate ID, and occurrence time.
- **MESSAGE-CONTRACT-MODEL-001** **[REQUIRED][TOPIC]** — Never directly serialize domain entities as integration events; use the distinct domain/integration type boundary defined by service layering.
- **MESSAGE-BATCH-001** **[REQUIRED][TOPIC]** — An outbox batch contains events from one aggregate and preserves batch sequence; split cross-aggregate events and never promise global ordering.
- **MESSAGE-PARTITION-001** **[REQUIRED][TOPIC]** — When aggregate ordering is required, use its stable ID as the partition key; do not use random or mutable keys and do not depend on global ordering.
- **MESSAGE-DESTINATION-CONFIG-001** **[REQUIRED][TOPIC]** — Configure and validate destinations, partition counts, weights, and routing algorithms centrally at startup; business code must not create unmanaged destinations dynamically.
- **MESSAGE-PARTITION-MIGRATION-001** **[REQUIRED][TOPIC]** — Before changing a partition count or routing algorithm, assess in-flight messages and ordering impact and define a migration plan.
- **MESSAGE-EVOLUTION-001** **[REQUIRED][TOPIC]** — Evolve event contracts backward-compatibly; new fields need compatible defaults, while deletion, rename, or semantic changes require a versioned migration.

## Consumption and delay

- **MESSAGE-IDEMPOTENCY-001** **[REQUIRED][TOPIC]** — Design consumers for at-least-once delivery and deduplicate by event ID or a stable business idempotency key; never assume a broker, outbox, or listener delivers once.
- **MESSAGE-LOCAL-IDEMPOTENCY-ATOMIC-001** **[REQUIRED][TOPIC]** — Commit completed-consumption deduplication and local business writes atomically in the same transaction; never mark consumed before separate fallible writes.
- **MESSAGE-EXTERNAL-IDEMPOTENCY-001** **[REQUIRED][TOPIC]** — For nontransactional external effects, use a recoverable idempotency protocol preventing missed processing or duplicated side effects on retries.
- **MESSAGE-ACK-COMMIT-001** **[REQUIRED][TOPIC]** — Acknowledge only after business processing and local transaction success.
- **MESSAGE-CONSUMER-RETRY-001** **[REQUIRED][TOPIC]** — On consumer failure, use classified bounded retries and backoff.
- **MESSAGE-CONSUMER-DEADLETTER-001** **[REQUIRED][TOPIC]** — Route unrecoverable messages or those exceeding retry thresholds to dead-letter or manual handling.
- **MESSAGE-LISTENER-001** **[REQUIRED][TOPIC]** — Listeners deserialize, validate, enter idempotency control, and must not swallow failures before acknowledgement; their layer responsibility follows the interfaces rules.
- **MESSAGE-DELAY-PURPOSE-001** **[REQUIRED][TOPIC]** — Use delayed messages only for error-tolerant retries, reminders, and auxiliary flows, never as precise timers or the sole guarantee of a core-business deadline.
- **MESSAGE-DELAY-STATE-001** **[REQUIRED][TOPIC]** — On delayed-message arrival recheck state and idempotency; expired or completed work must not repeat side effects.
- **MESSAGE-DELAY-PORT-001** **[REQUIRED][TOPIC]** — Use `DelayedOperations` with an explicit delay and keep RabbitMQ delayed-topology details out of business code.
- **MESSAGE-DELAY-QUORUM-001** **[REQUIRED][TOPIC]** — When at-least-once delayed delivery is required, use a quorum configuration supporting that semantics.
- **MESSAGE-DELAY-PARAMETER-001** **[REQUIRED][TOPIC]** — Validate delay levels, tick, and maximum TTL against supported ranges at startup.
- **MESSAGE-METRICS-001** **[REQUIRED][TOPIC]** — Monitor publication confirms/returns, outbox backlog, retries, consumer lag, and dead-letter counts.
- **MESSAGE-TRACE-CONTEXT-001** **[REQUIRED][TOPIC]** — Trace message processing with event ID, aggregate ID, and destination.
