# Logging Verification Rules

## Logging and sensitive data

- **TEST-LOG-MASKING-001** **[REQUIRED][TOPIC]** — In the smoke-test phase, exercise the centralized masking implementation for every supported sensitive-data type and verify its output format.
- **TEST-LOG-PRODUCTION-CONFIG-001** **[REQUIRED][TOPIC]** — In the smoke-test phase, prove that production logging emits no DEBUG records and exposes no plain-text sensitive data at any level.
