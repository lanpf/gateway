# API, Domain, and Application Rules

## Contents

- [API](#api)
- [Domain](#domain)
- [Application](#application)

## API

- **API-RESPONSIBILITY-001** — API defines stable Facade-centered contracts and contains interfaces and contract types without business implementations.
- **API-CONTENTS-001** — API may contain command, query, response, event, enum, and constants packages; enums represent finite values and constants represent headers, topics, event types, and similar fixed values.
- **API-FACADE-001** — Facade inputs implement `Request` and are complete, immutable API command/query records independent of HTTP binding and header injection; results are `BaseResult` implementations, using `PageResult<T>` for paged collections and `Result<T>` otherwise, including `Result<Void>` for no payload and `Result<List<T>>` for non-paged collections.
- **API-RESPONSE-001** — API response payloads use records consistently.
- **API-EVENT-001** — API integration events implement `IntegrationEvent`; they may be mapped from domain events but must use distinct types.

## Domain

- **DOMAIN-MODEL-001** — Use aggregate roots as consistency boundaries and define entities, value objects, domain services, repository contracts, and domain events around the domain model.
- **DOMAIN-ENTITY-ID-001** — A domain entity identifier extends `EntityId`. Each project provides type-specific bases such as `StringEntityId` and `LongEntityId`, centralizes value-type validation by overriding `validate()`, and overrides it again in a concrete identifier only for additional constraints.
- **DOMAIN-BEHAVIOR-001** — Aggregate roots and entities change state only through domain behavior; value objects are immutable; domain services contain only cross-aggregate rules or rules that do not belong to one aggregate.
- **NAME-DOMAIN-EFFECT-001** — Domain-service `*Effect` result types implement `DomainEffect` and return the domain events produced by that behavior through `events()`; return an empty collection rather than null when no event is produced.
- **DOMAIN-CLOCK-001** — Domain objects and services must not call system time directly; application services inject `Clock`, obtain business time once per use case, and pass the same value to related state changes and events.
- **DOMAIN-REPOSITORY-001** — Domain repositories express domain-object persistence contracts without exposing a persistence technology.
- **DOMAIN-EVENT-001** — Domain behavior decides whether a domain event occurred and its business content. Raw domain events carry occurrence time, event type, and business content only; they do not carry an EventStore persistence-record ID or depend on an ID generator. EventStore generates the global event ID when wrapping a `DomainEventEnvelope`.
- **DOMAIN-BOUNDARY-001** — Domain must not know API payloads, application commands/outputs/views, persistence DOs, concrete technologies, `Result<T>`, or `PageResult<T>`.

## Application

- **APPLICATION-RESPONSIBILITY-001** — Application orchestrates use cases, coordinates domain objects and ports, and does not implement domain rules.
- **APPLICATION-COMMAND-001** — Command services are write-use-case entry points, accept application commands, return command outputs or void, and own transaction boundaries.
- **APPLICATION-QUERY-001** — Query services are read-only, accept domain IDs, value objects, or query conditions, return application views, and use `PagedList<T>` for pagination.
- **APPLICATION-BOUNDARY-001** — Application may depend on domain, API integration-event contracts, and framework ports, but not API command/query/response types, protocol implementations, persistence DOs, or external response wrappers.
