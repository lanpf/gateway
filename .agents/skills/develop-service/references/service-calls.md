# Service Call Rules

## Contents

- [Internal service calls](#internal-service-calls)
- [Internal fallback contracts](#internal-fallback-contracts)
- [External service calls](#external-service-calls)
- [External Map boundaries](#external-map-boundaries)

## Internal service calls

- **SERVICE-CALL-INTERNAL-REUSE-001** **[REQUIRED][TOPIC]** — Prefer the callee's published API module and directly reuse request/response contracts rather than redefining equivalents.
- **SERVICE-CALL-INTERNAL-DEPENDENCY-001** **[REQUIRED][TOPIC]** — Depend only on the callee's public API, never its application, interfaces, or concrete implementation modules.

## Internal fallback contracts

- **SERVICE-CALL-FALLBACK-CONDITION-001** **[REQUIRED][TOPIC]** — Define local adapter types only when the published API cannot be referenced or its public contract does not fit the current call boundary.
- **SERVICE-CALL-FALLBACK-REQUEST-001** **[REQUIRED][TOPIC]** — A local fallback request implements `Request` and follows the internal-request naming rule.
- **SERVICE-CALL-FALLBACK-RESPONSE-001** **[REQUIRED][TOPIC]** — Use `Result<T>` for ordinary fallback responses and `PageResult<T>` for paged responses.
- **SERVICE-CALL-FALLBACK-NAME-001** **[REQUIRED][TOPIC]** — Prefer a fallback response T name consistent with the published contract, but use local semantic names when anti-corruption, protocol, or semantic differences require them; never incorrectly reuse a Java type merely to preserve a name.

## External service calls

- **SERVICE-CALL-EXTERNAL-BOUNDARY-001** **[REQUIRED][TOPIC]** — Confine external service calls to a local adapter boundary in the concrete infrastructure adapter module and use locally owned request and response types that follow the external-payload naming rule; external SDK types, protocol models, and serialization details must not cross into application or domain, and mapping between local and external models is explicit.
- **SERVICE-CALL-EXTERNAL-PAYLOAD-001** **[REQUIRED][TOPIC]** — Define typed models for fixed-structure external requests and responses, except the narrowly defined small path/query/header parameter exception; application/json uses `*RequestPayload` and `*ResponsePayload`.
- **SERVICE-CALL-EXTERNAL-BINDING-001** **[REQUIRED][TOPIC]** — HTTP method and parameter location do not change payload naming; a client adapter, dedicated encoder, or mapper binds payload data to GET query/path/header parameters or POST form/body data.
- **SERVICE-CALL-EXTERNAL-PAYLOAD-BEHAVIOR-001** **[REQUIRED][TOPIC]** — External-service payloads describe protocol data only and must not implement `queryParams()`, `headers()`, serialization, HTTP parameter assembly, or a dependency on one HTTP client.
- **SERVICE-CALL-EXTERNAL-PARAMETER-001** **[DEFAULT][TOPIC]** — A client may directly declare a small fixed set of path/query/header parameters with no reuse need instead of creating a request payload. This exception excludes fixed JSON bodies and responses and never permits assembling fixed fields in a Map.

## External Map boundaries

- **SERVICE-CALL-EXTERNAL-MAP-001** **[REQUIRED][TOPIC]** — Use a Map to model an external protocol only when it formally defines dynamic key/value content such as `additionalProperties`, metadata, labels, dynamic identifier keys, runtime-only fields, or uninterpreted extension forwarding.
- **SERVICE-CALL-EXTERNAL-MAP-ADAPTER-001** **[REQUIRED][TOPIC]** — Use a Map only inside an infrastructure adapter when an SDK or low-level client accepts only a Map, or when form, multipart, or signing input cannot be encoded from a typed payload by the available client.
- **SERVICE-CALL-FIXED-MODEL-001** **[REQUIRED][TOPIC]** — Use typed payloads for fixed field sets; never use Map merely to save type definitions, reuse a generic request, assemble a few fixed fields, or avoid a mapper.
- **SERVICE-CALL-MAP-CONFINEMENT-001** **[REQUIRED][TOPIC]** — When Map adaptation is necessary, confine construction and conversion to the matching infrastructure adapter; never expose it through ports or to application/domain.
- **SERVICE-CALL-MAP-CONSTANT-001** **[REQUIRED][TOPIC]** — Use external-service-specific constants for fixed Map protocol keys and keep them in the corresponding infrastructure adapter.
