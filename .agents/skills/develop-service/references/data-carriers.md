# Layered Data Carrier Rules

## API and domain

- **API-RESPONSE-001** **[REQUIRED][TOPIC]** — Use records for API response payloads.
- **DATA-API-EVENT-001** **[REQUIRED][TOPIC]** — Use a record for an API event when serialization, RPC, OpenFeign, and clients support constructor binding; use an ordinary JavaBean class when an existing consumer cannot support it.
- **DATA-API-COMPAT-001** **[REQUIRED][TOPIC]** — For public Java APIs, evaluate the binary-constructor compatibility impact before adding a record component.
- **DATA-DOMAIN-RECORD-001** **[DEFAULT][TOPIC]** — Prefer records for domain IDs, value objects, effects, and immutable events when inheritance is unnecessary.
- **DATA-DOMAIN-CLASS-001** **[REQUIRED][TOPIC]** — Use ordinary classes for aggregate roots, entities, mutable state, or complex behavior, following the Java type-selection rules.
- **DATA-INTERFACES-001** **[REQUIRED][TOPIC]** — Use ordinary mutable classes for interfaces HTTP requests so JSON binding and Client, Channel, or Device header injection can populate them; they may extend the required Client context type but must not be used as Facade inputs.

## Application and persistence

- **DATA-APPLICATION-RECORD-001** **[DEFAULT][TOPIC]** — Prefer records for immutable application commands, outputs, query conditions, and views.
- **DATA-APPLICATION-COMPONENT-001** **[REQUIRED][TOPIC]** — Services, repositories, gateways, and mappers use ordinary classes or their corresponding interfaces, never records.
- **DATA-PERSISTENCE-PROJECTION-001** **[DEFAULT][TOPIC]** — Read-only persistence projections may use records by default.
- **DATA-PERSISTENCE-DO-001** **[REQUIRED][TOPIC]** — DOs, JPA entities, and types requiring no-argument constructors or setter mapping use ordinary classes.
