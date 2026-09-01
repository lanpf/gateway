# Logging and Sensitive Data Rules

## Logging usage

- **LOG-SLF4J-001** — Declare loggers with `@Slf4j` and write messages with SLF4J parameter placeholders; do not build message text by string concatenation, and pass the exception object as the last argument instead of embedding it in the message.
- **LOG-LEVEL-001** — Use `ERROR` for failures that need human intervention, including exhausted retries, unrecoverable errors, and unclassified integrity failures.
- **LOG-LEVEL-002** — Use `WARN` for exceptions that have already recovered or degraded as expected, including retried-then-succeeded operations, expected idempotent conflicts, and degraded execution.
- **LOG-LEVEL-003** — Use `INFO` for audit points of key business actions, state changes, and external interactions; reserve `DEBUG` for development diagnosis and keep it disabled in production by default.
- **LOG-CONTEXT-001** — Every log statement carries locatable context such as the use-case or job name, the sanitized business key, and the scene or shard identifier; the framework injects tracing context, and business code must not hand-assemble trace fields.
- **LOG-METRICS-001** — Do not replace metrics, alerts, or distributed tracing with logs, and do not emit one `INFO` record per occurrence for high-frequency recurring events.

## Sensitive data and masking

- **SENSITIVE-DATA-001** — Sensitive data means passwords, tokens, keys, certificates, government IDs, phone numbers, bank-card numbers, biometrics, precise addresses, and similar personal data, plus complete request or response payloads.
- **SENSITIVE-LOG-001** — Sensitive data must never enter any log in plain text, including message text, parameters, MDC values, and exception information.
- **SENSITIVE-MASK-001** — When a log must reference a sensitive business key, use the centrally implemented masking or irreversible digest; do not scatter masking logic across business code, keep the data type recognizable while masking the value itself (for example a phone number as `138****1234`), and keep one consistent mask format per sensitive type.
