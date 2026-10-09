# Logging and Sensitive Data Rules

## Logging usage

- **LOG-PARAMETERIZED-001** **[REQUIRED][BASELINE]** — Declare loggers under the Lombok rules and use SLF4J placeholders instead of concatenating message text.
- **LOG-EXCEPTION-ARGUMENT-001** **[REQUIRED][BASELINE]** — When logging an exception, pass the exception object as the last argument instead of embedding it in message text.
- **LOG-SEVERITY-001** **[REQUIRED][BASELINE]** — Choose severity by impact and handling: ERROR for failures requiring human intervention such as exhausted retries, unrecoverable or unclassified integrity failures; WARN for noteworthy abnormal contention, recovery, or degradation; INFO for normal idempotency and key business/external-interaction audit points; DEBUG for diagnosis. Exception translation alone does not make a normal idempotent hit WARN.
- **LOG-PRODUCTION-DEBUG-001** **[REQUIRED][BASELINE]** — Disable DEBUG in production by default.
- **LOG-BUSINESS-CONTEXT-001** **[REQUIRED][BASELINE]** — Include locatable business context such as use-case/job name, sanitized business key, and scene or shard identifier.
- **LOG-TRACE-CONTEXT-001** **[REQUIRED][BASELINE]** — Let the framework inject tracing context; business code must not manually assemble trace fields.
- **LOG-OBSERVABILITY-BOUNDARY-001** **[REQUIRED][BASELINE]** — Logs must not replace metrics, alerts, or distributed tracing.
- **LOG-RECURRING-CONTROL-001** **[REQUIRED][BASELINE]** — Aggregate, rate-limit, or sample high-frequency recurring operational records instead of INFO per occurrence or escalation to WARN to bypass controls. Aggregates retain scene, time window, count, and outcome.
- **LOG-AUDIT-COMPLETENESS-001** **[REQUIRED][BASELINE]** — Explicit per-event audit records must use a controlled audit channel without sampling or loss.

## Sensitive data and masking

- **SENSITIVE-DATA-001** **[REQUIRED][BASELINE]** — Sensitive data means passwords, tokens, keys, certificates, government IDs, phone numbers, bank-card numbers, biometrics, precise addresses, and similar personal data, plus complete request or response payloads.
- **SENSITIVE-PLAINTEXT-001** **[REQUIRED][BASELINE]** — Never emit sensitive data in plain text in message text, parameters, MDC, or exception information.
- **SENSITIVE-CENTRAL-MASK-001** **[REQUIRED][BASELINE]** — Correlate sensitive business keys only through centralized masking or irreversible digests; do not scatter such implementations in business code.
- **SENSITIVE-MASK-001** **[REQUIRED][BASELINE]** — When a log must reference a sensitive business key, use the centrally implemented masking or irreversible digest; do not scatter masking logic across business code, keep the data type recognizable while masking the value itself (for example a phone number as `138****1234`), and keep one consistent mask format per sensitive type.
