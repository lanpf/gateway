# Service Call Rules

## Contents

- [Internal service calls](#internal-service-calls)
- [Internal fallback contracts](#internal-fallback-contracts)
- [External service calls](#external-service-calls)
- [External Map boundaries](#external-map-boundaries)

## Internal service calls

- **SERVICE-CALL-INTERNAL-API-001** **[REQUIRED][TOPIC]** — Internal service calls prefer the callee's published API module and reuse its request and response contracts instead of redefining equivalent objects; depend only on the public API, never on the callee's application, interfaces, or implementation modules.

## Internal fallback contracts

- **SERVICE-CALL-INTERNAL-FALLBACK-001** **[REQUIRED][TOPIC]** — Define local adapter types only when the callee's API module cannot be referenced or its public contract does not fit the call boundary. The local request implements `Request` and follows the internal-request naming rule; ordinary responses use `Result<T>` and paged responses use `PageResult<T>`. Prefer a `T` type name consistent with the corresponding published contract, but use local semantic naming when an anti-corruption layer, protocol difference, or semantic difference requires a distinct Java type rather than forcing name or type reuse.

## External service calls

- **SERVICE-CALL-EXTERNAL-BOUNDARY-001** **[REQUIRED][TOPIC]** — Confine external service calls to a local adapter boundary in the concrete infrastructure adapter module and use locally owned request and response types that follow the external-payload naming rule; external SDK types, protocol models, and serialization details must not cross into application or domain, and mapping between local and external models is explicit.
- **SERVICE-CALL-EXTERNAL-PAYLOAD-001** **[REQUIRED][TOPIC]** — Define typed protocol models for fixed-structure external requests and responses; when they use `application/json`, name them `*RequestPayload` and `*ResponsePayload`.
- **SERVICE-CALL-EXTERNAL-BINDING-001** **[REQUIRED][TOPIC]** — HTTP method and parameter location do not change payload naming; a client adapter, dedicated encoder, or mapper binds payload data to GET query/path/header parameters or POST form/body data.
- **SERVICE-CALL-EXTERNAL-PAYLOAD-BEHAVIOR-001** **[REQUIRED][TOPIC]** — External-service payloads describe protocol data only and must not implement `queryParams()`, `headers()`, serialization, HTTP parameter assembly, or a dependency on one HTTP client.
- **SERVICE-CALL-EXTERNAL-PARAMETER-001** **[DEFAULT][TOPIC]** — A client method may declare a small fixed parameter set explicitly when it has no reuse need.

## External Map boundaries

- **SERVICE-CALL-EXTERNAL-MAP-001** **[REQUIRED][TOPIC]** — Use a Map to model an external protocol only when it formally defines dynamic key/value content such as `additionalProperties`, metadata, labels, dynamic identifier keys, runtime-only fields, or uninterpreted extension forwarding.
- **SERVICE-CALL-EXTERNAL-MAP-ADAPTER-001** **[REQUIRED][TOPIC]** — Use a Map only inside an infrastructure adapter when an SDK or low-level client accepts only a Map, or when form, multipart, or signing input cannot be encoded from a typed payload by the available client.
- **SERVICE-CALL-EXTERNAL-MAP-002** **[REQUIRED][TOPIC]** — Use a typed payload when the field set is fixed; do not use a Map merely to avoid defining a type or mapper, reuse a generic request object, or quickly assemble a few fields. When a Map is necessary, confine its construction, conversion, and fixed protocol-field names to the matching infrastructure adapter, use external-service-specific constants for fixed keys, and never expose it through a port or to application or domain.
