# Service Naming Rules

## General names

- **NAME-INTERFACE-001** — Name interfaces by business role, port, or technical contract without `I*` or `*Interface`; use `Abstract*` for abstract classes and reserve `Base*` for framework or shared base types.
- **NAME-IMPLEMENTATION-001** — Do not use generic `*Impl`; name implementations by default role, technology, adapter responsibility, or strategy.
- **NAME-RESULT-001** — Do not suffix payloads, command outputs, query views, or effects with `*Result`; reserve `Result<T>` and `PageResult<T>` for framework response wrappers.
- **NAME-RESPONSE-001** — Only API payloads may use `*ApiResponse`, and only application return types may use `*Response`; other layers must not use a `*Response` suffix.
- **NAME-REMOTE-PAYLOAD-001** — Suffix REST-client remote-call return types with `*Payload`.
- **NAME-MAPPER-001** — Name mapper contracts by layer responsibility, place MapStruct implementations in the contract package's `mapstruct` subpackage, and name them `*MapStructMapper`.

## Layer names

- **NAME-API-001** — Use `*ApiCommand` with `*ApiCommandOutput`, `*ApiQuery` with `*ApiQueryView`, `*ApiResponse` for reusable command/query payloads, plus `*ApiEnum`, `*ApiConstants`, `*ApiEvent`, and `*Facade` in API.
- **NAME-DOMAIN-001** — Use `*Effect` for domain-service results, `*Repository` for repository contracts, and `*Event` for domain events.
- **NAME-APPLICATION-001** — Use `*Command`, `*Output`, `*CommandService`, `*View`, and `*QueryService` in application; paged queries return `PagedList<*View>`.
- **NAME-APPLICATION-RESPONSE-001** — Use `*Response` only when an application command and query return type must be reused.
- **NAME-INFRA-001** — Use `*RepositoryAdapter`, technology-specific `*PersistenceRepository`, `*DO`, and `*PersistenceConfiguration` in infrastructure.
- **NAME-INTERFACES-001** — Place protocol-neutral Facade implementations in `interfaces.facade` as `Default*Facade`; use `*RpcAdapter` only for necessary technology-specific RPC adapters.
- **NAME-CLIENT-001** — Name OpenFeign clients `*FeignClient`.
