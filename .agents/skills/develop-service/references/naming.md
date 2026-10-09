# Service Naming Rules

## General names

- **NAME-INTERFACE-ROLE-001** **[REQUIRED][BASELINE]** — Name interfaces by business role, port, or technical contract without `I*` or `*Interface`.
- **NAME-ABSTRACT-BASE-001** **[REQUIRED][BASELINE]** — Name abstract classes `Abstract*`; reserve `Base*` for framework or shared base types.
- **NAME-IMPLEMENTATION-001** **[REQUIRED][BASELINE]** — Do not use generic `*Impl`; name implementations by default role, technology, adapter responsibility, or strategy.
- **NAME-AUTO-CONFIGURATION-001** **[REQUIRED][BASELINE]** — Spring Boot auto-configuration entry classes must use the `*AutoConfiguration` suffix. All other types must be named by their actual responsibility without the `Configuration` suffix, except component-scanned wiring classes.
- **NAME-CONFIGURATION-001** **[REQUIRED][BASELINE]** — Name component-scanned wiring classes with the `*Configuration` suffix.
- **NAME-PROPERTIES-001** **[REQUIRED][BASELINE]** — Name configuration-property binding classes with the `*Properties` suffix.
- **NAME-RESULT-001** **[REQUIRED][BASELINE]** — Do not suffix payloads, command outputs, query views, or effects with `*Result`; reserve `Result<T>` and `PageResult<T>` for framework response wrappers.
- **NAME-API-SHARED-RESPONSE-001** **[REQUIRED][BASELINE]** — Use `*ApiResponse` only for a shared API payload return type.
- **NAME-REMOTE-PAYLOAD-001** **[REQUIRED][BASELINE]** — Name a locally owned external-service protocol type with the external service name, action or resource, and `RequestPayload` or `ResponsePayload` suffix, such as `GithubSearchRepositoriesRequestPayload`; do not use names such as `RequestPayload` or `CreateRequestPayload` that omit the external-service identity or call semantics.
- **NAME-INTERNAL-REQUEST-001** **[REQUIRED][BASELINE]** — Name a locally defined internal-service request type `*InternalRequest` when the callee's published API contract cannot be reused.
- **NAME-MAPPER-TYPE-001** **[REQUIRED][BASELINE]** — Name conversion contracts by layer responsibility and handwritten MapStruct mapper types `*MapStructMapper`.
- **NAME-MAPPER-PACKAGE-001** **[REQUIRED][BASELINE]** — Place the handwritten MapStruct mapper in its contract package's `mapstruct` subpackage.
- **NAME-KEY-RESOLVER-SUFFIX-001** **[REQUIRED][BASELINE]** — Name each concrete business-scenario resource-key handler `*KeyResolver`.
- **NAME-KEY-RESOLVER-BASE-001** **[REQUIRED][BASELINE]** — Concrete scenario key resolvers extend `AbstractKeyResolver`; runtime responsibilities follow resource-key rules.
- **NAME-PACKAGE-001** **[REQUIRED][BASELINE]** — Use lowercase package names that express a stable responsibility or change boundary; do not use `misc`, `temp`, or `other`, and do not place business types in generic `util` or `common` packages. Package hierarchy and grouping follow the service layer rules.

## Layer names

- **NAME-API-001** **[REQUIRED][BASELINE]** — Use `*ApiCommand`, `*ApiOutput`, `*ApiQuery`, `*ApiView`, `*ApiEnum`, `*ApiConstants`, `*ApiEvent`, `*CommandFacade`, and `*QueryFacade` in API.
- **NAME-DOMAIN-001** **[REQUIRED][BASELINE]** — Use `*Effect` for domain-service results, `*Repository` for repository contracts, and `*Event` for domain events.
- **NAME-APPLICATION-001** **[REQUIRED][BASELINE]** — Use `*CommandService`, `*Command`, `*Output`, `*QueryService`, `*Query`, and `*View` in application; follow the application layering rules for paged return contracts.
- **NAME-APPLICATION-RESPONSE-001** **[REQUIRED][BASELINE]** — Use `*Response` only when an application command and query return type must be reused.
- **NAME-INFRA-001** **[REQUIRED][BASELINE]** — Use `*RepositoryAdapter`, technology-specific `*PersistenceRepository`, `*DO`, and `*PersistenceAutoConfiguration` in infrastructure.
- **NAME-INTERFACES-001** **[REQUIRED][BASELINE]** — Place protocol-neutral Facade implementations in `interfaces.facade` as `Default*Facade`; use `*RpcAdapter` only for necessary technology-specific RPC adapters.
- **NAME-CLIENT-001** **[REQUIRED][BASELINE]** — Name OpenFeign clients `*FeignClient`.
