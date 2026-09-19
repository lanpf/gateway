# Service Naming Rules

## General names

- **NAME-INTERFACE-001** **[REQUIRED][BASELINE]** — Name interfaces by business role, port, or technical contract without `I*` or `*Interface`; use `Abstract*` for abstract classes and reserve `Base*` for framework or shared base types.
- **NAME-IMPLEMENTATION-001** **[REQUIRED][BASELINE]** — Do not use generic `*Impl`; name implementations by default role, technology, adapter responsibility, or strategy.
- **NAME-AUTO-CONFIGURATION-001** **[REQUIRED][BASELINE]** — Spring Boot auto-configuration entry classes must use the `*AutoConfiguration` suffix. All other types must be named by their actual responsibility without the `Configuration` suffix, except component-scanned wiring classes.
- **NAME-CONFIGURATION-001** **[REQUIRED][BASELINE]** — Name component-scanned wiring classes with the `*Configuration` suffix.
- **NAME-PROPERTIES-001** **[REQUIRED][BASELINE]** — Name configuration-property binding classes with the `*Properties` suffix.
- **NAME-RESULT-001** **[REQUIRED][BASELINE]** — Do not suffix payloads, command outputs, query views, or effects with `*Result`; reserve `Result<T>` and `PageResult<T>` for framework response wrappers.
- **NAME-RESPONSE-001** **[REQUIRED][BASELINE]** — Only API payloads may use `*ApiResponse`, and only application return types may use `*Response`; other layers must not use a `*Response` suffix.
- **NAME-REMOTE-PAYLOAD-001** **[REQUIRED][BASELINE]** — Name a locally owned external-service protocol type with the external service name, action or resource, and `RequestPayload` or `ResponsePayload` suffix, such as `GithubSearchRepositoriesRequestPayload`; do not use names such as `RequestPayload` or `CreateRequestPayload` that omit the external-service identity or call semantics.
- **NAME-INTERNAL-REQUEST-001** **[REQUIRED][BASELINE]** — Name a locally defined internal-service request type `*InternalRequest` when the callee's published API contract cannot be reused.
- **NAME-MAPPER-001** **[REQUIRED][BASELINE]** — Name mapper contracts by layer responsibility, place MapStruct implementations in the contract package's `mapstruct` subpackage, and name them `*MapStructMapper`.
- **NAME-KEY-RESOLVER-001** **[REQUIRED][BASELINE]** — Name each concrete scenario resource-key handler `*KeyResolver` and make it extend `AbstractKeyResolver`; its runtime behavior follows the resource-key rules.
- **NAME-PACKAGE-001** **[REQUIRED][BASELINE]** — Use lowercase package names that express a stable responsibility or change boundary; do not use `misc`, `temp`, or `other`, and do not place business types in generic `util` or `common` packages. Package hierarchy and grouping follow the service layer rules.

## Layer names

- **NAME-API-001** **[REQUIRED][BASELINE]** — Use `*ApiCommand`, `*ApiOutput`, `*ApiQuery`, `*ApiView`, `*ApiEnum`, `*ApiConstants`, `*ApiEvent`, `*CommandFacade`, and `*QueryFacade` in API.
- **NAME-DOMAIN-001** **[REQUIRED][BASELINE]** — Use `*Effect` for domain-service results, `*Repository` for repository contracts, and `*Event` for domain events.
- **NAME-APPLICATION-001** **[REQUIRED][BASELINE]** — Use `*CommandService`, `*Command`, `*Output`, `*QueryService`, `*Query`, and `*View` in application; paged queries return `PagedList<*View>`.
- **NAME-APPLICATION-RESPONSE-001** **[REQUIRED][BASELINE]** — Use `*Response` only when an application command and query return type must be reused.
- **NAME-INFRA-001** **[REQUIRED][BASELINE]** — Use `*RepositoryAdapter`, technology-specific `*PersistenceRepository`, `*DO`, and `*PersistenceAutoConfiguration` in infrastructure.
- **NAME-INTERFACES-001** **[REQUIRED][BASELINE]** — Place protocol-neutral Facade implementations in `interfaces.facade` as `Default*Facade`; use `*RpcAdapter` only for necessary technology-specific RPC adapters.
- **NAME-CLIENT-001** **[REQUIRED][BASELINE]** — Name OpenFeign clients `*FeignClient`.
