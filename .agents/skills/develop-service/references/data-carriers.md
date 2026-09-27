# Layered Data Carrier Rules

## API and domain

- **API-RESPONSE-001** **[REQUIRED][TOPIC]** — Use records for API response payloads.
- **DATA-API-EVENT-001** **[REQUIRED][TOPIC]** — Use a record for an API event when serialization, RPC, OpenFeign, and clients support constructor binding; use an ordinary JavaBean class when an existing consumer cannot support it.
- **DATA-API-COMPAT-001** **[REQUIRED][TOPIC]** — For public Java APIs, evaluate the binary-constructor compatibility impact before adding a record component.
- **DATA-DOMAIN-001** **[REQUIRED][TOPIC]** — Prefer records for domain IDs, value objects, effects, and immutable domain events when inheritance is unnecessary; use classes for aggregate roots, entities, framework bases, mutable state, and complex behavior.
- **DATA-INTERFACES-001** **[REQUIRED][TOPIC]** — Use ordinary mutable classes for interfaces HTTP requests so JSON binding and Client, Channel, or Device header injection can populate them; they may extend the required Client context type but must not be used as Facade inputs.

## Application and persistence

- **DATA-APPLICATION-001** **[REQUIRED][TOPIC]** — Prefer records for immutable application commands, command outputs, query conditions, and views; services, repositories, gateways, and mappers remain classes.
- **DATA-PERSISTENCE-001** **[REQUIRED][TOPIC]** — Read-only persistence projections may be records; DOs, JPA entities, and no-argument or setter-bound types remain classes.
