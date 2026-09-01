# Distributed ID Rules

## Generation and use

- **ID-GENERATOR-001** — Generate globally unique aggregate, domain-event persistence-envelope, integration-event, and distributed-data identifiers through the injected `LongIdGenerator`; do not use database auto-increment, random values, timestamp concatenation, or a business-owned generator.
- **ID-NAME-001** — Pass a stable, configured, low-cardinality ID-space name to `nextId(name)`; do not derive generator names from user input, request IDs, or per-record business keys.
- **ID-FAILFAST-001** — Register and validate every generator name before deployment; missing configuration fails startup or the use case and must not silently fall back to a local algorithm or database sequence.
- **ID-BOUNDARY-001** — Generate aggregate and other business IDs through framework ports in application/infrastructure and pass them into domain; generate domain-event persistence IDs at the EventStore infrastructure boundary when building envelopes. Domain must not depend on `LongIdGenerator`, CosId, or starter implementation types.
- **ID-SEMANTICS-001** — Treat generated IDs as unique and trend-increasing only; do not assume they are contiguous, gap-free, strictly chronological, or safe to decode into business meaning.
- **ID-STRING-001** — Non-Long string identifiers such as UUIDs or external-system identities are outside the `LongIdGenerator` scope; business code must not invent new generation algorithms, the identifier source is defined by the project domain document, and identifier types extend the project-level `EntityId` bases.
