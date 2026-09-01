# Layered Data Carrier Rules

## API and domain

- **DATA-API-001** — Prefer records for API commands and queries, and always use records for API responses; these contracts are complete, immutable, and independent of HTTP binding and header injection. Use records for API events when serialization, RPC, OpenFeign, and client compatibility support immutable constructor binding, and use an ordinary JavaBean class for API events when an existing consumer cannot support constructor binding.
- **DATA-API-COMPAT-001** — For public Java APIs, evaluate the binary-constructor compatibility impact before adding a record component.
- **DATA-DOMAIN-001** — Prefer records for domain IDs, value objects, effects, and immutable domain events when inheritance is unnecessary; use classes for aggregate roots, entities, framework bases, mutable state, and complex behavior.
- **DATA-INTERFACES-001** — Use ordinary mutable classes for interfaces HTTP requests so JSON binding and Client, Channel, or Device header injection can populate them; they may extend the required Client context type but must not be used as Facade inputs.

## Application and persistence

- **DATA-APPLICATION-001** — Prefer records for immutable application commands, command outputs, query conditions, and views; services, repositories, gateways, and mappers remain classes.
- **DATA-PERSISTENCE-001** — Read-only persistence projections may be records; DOs, JPA entities, and no-argument or setter-bound types remain classes.
