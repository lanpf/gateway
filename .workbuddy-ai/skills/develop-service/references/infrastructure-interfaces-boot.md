# Infrastructure, Interfaces, Client, and Boot Rules

## Infrastructure and configuration

- **INFRA-RESPONSIBILITY-001** **[REQUIRED][TOPIC]** — Infrastructure provides repository, ID, event-storage, messaging, scheduling, and other technical adapters and implements ports defined by domain or application without owning domain rules or use-case orchestration.
- **INFRA-ASYNC-001** **[REQUIRED][TOPIC]** — Adapt domain event storage through the port defined by framework-domain; domain and application must not depend on a concrete domain-event-store implementation. EventStore uses `LongIdGenerator` to assign each persistence envelope a trend-increasing Long ID, and persistence adapters must not inject that technical ID back into the raw domain event.
- **INFRA-CONFIG-001** **[REQUIRED][TOPIC]** — For JavaBean configuration binding, collection and nested-object fields are final, expose getters without setters, and begin as empty binding containers without business defaults.
- **INFRA-CONFIG-002** **[REQUIRED][TOPIC]** — Required configuration collections use `@NotEmpty` so missing configuration fails startup rather than running silently with an empty value.
- **INFRA-DURATION-001** **[REQUIRED][TOPIC]** — A standalone minimum for `Duration` uses both `@NotNull` and Hibernate Validator `@DurationMin` with explicit units; the module declares `hibernate-validator` directly.
- **INFRA-CROSS-FIELD-001** **[REQUIRED][TOPIC]** — Use `@AssertTrue` and similar type-level validation only for relationships between fields.
- **DEP-BEAN-001** **[REQUIRED][TOPIC]** — Reuse infrastructure Beans supplied by the runtime framework instead of declaring another Bean with the same semantics.

## Interfaces, client, and boot

- **INTERFACES-RESPONSIBILITY-001** **[REQUIRED][TOPIC]** — Interfaces provides protocol-neutral Facade implementations and REST, RPC, and message entry points without domain rules or use-case orchestration.
- **INTERFACES-FACADE-001** **[REQUIRED][TOPIC]** — Facade implementations convert API/application objects, call application services, and wrap responses with `Result<T>` or `PageResult<T>`.
- **INTERFACES-REST-001** **[REQUIRED][TOPIC]** — Controllers handle routing, binding, and necessary protocol context only, convert the HTTP request into an API command/query, then invoke a Facade contract.
- **INTERFACES-HEADER-CONTEXT-001** **[REQUIRED][TOPIC]** — Inject or resolve Client, Channel, Device, and other HTTP header context only in interfaces; API, application, and domain must not depend on the HTTP header binding mechanism.
- **INTERFACES-REST-CONTEXT-001** **[REQUIRED][TOPIC]** — After header injection and validation, each controller business request converges into one ordinary mutable `@Valid` HTTP request extending `ClientRequest`.
- **INTERFACES-REST-CONTEXT-002** **[REQUIRED][TOPIC]** — Implement the composable `ChannelContext` and `AuthenticatedSessionContext` only when the endpoint really needs trusted channel or authenticated-session context.
- **INTERFACES-REST-CONTEXT-003** **[REQUIRED][TOPIC]** — Do not split business fields into path or query parameters alongside a separate client-context argument; for endpoints without a business body, declare a concrete request type matching the required contexts and let the shared argument resolver build and validate it from protected headers.
- **INTERFACES-FACADE-BOUNDARY-001** **[REQUIRED][TOPIC]** — After header injection and validation, interfaces converts its mutable HTTP request into a complete immutable API command/query record; Facades must not accept interfaces HTTP request types.
- **INTERFACES-RPC-001** **[REQUIRED][TOPIC]** — Publish the same Facade Bean directly when the RPC technology supports it; add a technology-specific RPC adapter only for incompatible models, semantics, metadata, or exception handling.
- **INTERFACES-MESSAGE-001** **[REQUIRED][TOPIC]** — Message listeners are interfaces-layer protocol entries and are not required to pass through a Facade; messaging rules own consumption reliability and processing flow.
- **CLIENT-FEIGN-001** **[REQUIRED][TOPIC]** — OpenFeign clients match Facade method signatures, reuse API contract types and response wrappers, and contain no business model, use case, or conversion rules.
- **BOOT-RESPONSIBILITY-001** **[REQUIRED][TOPIC]** — Boot owns startup, runtime configuration, and packaging; dependency rules own technology implementation selection.
- **BOOT-BOUNDARY-001** **[REQUIRED][TOPIC]** — Boot contains no domain rules, application orchestration, protocol adapters, or business types.
