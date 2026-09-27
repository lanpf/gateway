# Logging and Sensitive Data Rules

## Logging usage

- **LOG-SLF4J-001** **[REQUIRED][BASELINE]** — Write log messages with SLF4J parameter placeholders rather than string concatenation, and pass the exception object as the last argument instead of embedding it in message text.
- **LOG-LEVEL-001** **[REQUIRED][BASELINE]** — Use `ERROR` for failures that need human intervention, including exhausted retries, unrecoverable errors, and unclassified integrity failures.
- **LOG-LEVEL-002** **[REQUIRED][BASELINE]** — Choose severity by impact and handling needs rather than exception class: `WARN` denotes noteworthy abnormal contention, recovery, or degradation. A normal idempotent hit does not become `WARN` merely because exception translation was involved.
- **LOG-LEVEL-003** **[REQUIRED][BASELINE]** — Use `INFO` for normal idempotent outcomes and audit points of key business actions, state changes, and external interactions. Use `DEBUG` for diagnosis and disable it in production by default.
- **LOG-CONTEXT-001** **[REQUIRED][BASELINE]** — Every log statement carries locatable context such as the use-case or job name, the sanitized business key, and the scene or shard identifier; the framework injects tracing context, and business code must not hand-assemble trace fields.
- **LOG-METRICS-001** **[REQUIRED][BASELINE]** — Do not replace metrics, alerts, or tracing with logs. Aggregate, rate-limit, or sample high-frequency recurring operational records rather than emitting `INFO` per occurrence or escalating to `WARN` to bypass controls; aggregates retain scene, time window, count, and outcome. Explicit per-event audit requirements must use a controlled audit channel without sampling or loss.

## Sensitive data and masking

- **SENSITIVE-DATA-001** **[REQUIRED][BASELINE]** — Sensitive data means passwords, tokens, keys, certificates, government IDs, phone numbers, bank-card numbers, biometrics, precise addresses, and similar personal data, plus complete request or response payloads.
- **SENSITIVE-LOG-001** **[REQUIRED][BASELINE]** — Sensitive data must never enter any log in plain text, including message text, parameters, MDC values, and exception information.
- **SENSITIVE-MASK-001** **[REQUIRED][BASELINE]** — When a log must reference a sensitive business key, use the centrally implemented masking or irreversible digest; do not scatter masking logic across business code, keep the data type recognizable while masking the value itself (for example a phone number as `138****1234`), and keep one consistent mask format per sensitive type.
