# Distributed ID Rules

## Generation and use

- **ID-GENERATOR-001** **[REQUIRED][TOPIC]** — Generate globally unique aggregate, domain-event persistence-envelope, integration-event, and distributed-data identifiers through the injected `LongIdGenerator`; do not use database auto-increment, random values, timestamp concatenation, or a business-owned generator.
- **ID-NAME-001** **[REQUIRED][TOPIC]** — Pass a stable, configured, low-cardinality ID-space name to `nextId(name)`; do not derive generator names from user input, request IDs, or per-record business keys.
- **ID-GENERATOR-REGISTRATION-001** **[REQUIRED][TOPIC]** — Register and validate generator names and corresponding CosId configuration before deployment.
- **ID-GENERATOR-MISSING-001** **[REQUIRED][TOPIC]** — A missing generator fails startup or its use case; never silently fall back to local algorithms or database auto-increment.
- **ID-BUSINESS-BOUNDARY-001** **[REQUIRED][TOPIC]** — Generate aggregate and other business identifiers through framework ports in application/infrastructure and pass them into domain. Domain must not depend on `LongIdGenerator`, CosId, or starter types.
- **ID-EVENTSTORE-BOUNDARY-001** **[REQUIRED][TOPIC]** — Domain-event persistence identifier generation follows the canonical EventStore and Envelope identity rule in service layering.
- **ID-SEMANTICS-001** **[REQUIRED][TOPIC]** — Treat generated IDs as unique and trend-increasing only; do not assume they are contiguous, gap-free, strictly chronological, or safe to decode into business meaning.
- **ID-STRING-SOURCE-001** **[REQUIRED][TOPIC]** — Non-Long string identifiers such as UUIDs or external identities are outside `LongIdGenerator`; never invent business-owned generation algorithms, and document their source in the project domain document.
- **ID-STRING-TYPE-001** **[REQUIRED][TOPIC]** — Non-Long domain identifier types follow the layered-service project-level `EntityId` base constraints.
- **ID-NODE-CLOCK-001** **[REQUIRED][TOPIC]** — Manage node or machine identity and clock-rollback policy in shared infrastructure configuration; business modules must not override or hard-code node identity.
