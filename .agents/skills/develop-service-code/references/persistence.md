# Persistence Rules

## Contents

- [Boundary, concurrency, and transactions](#boundary,-concurrency,-and-transactions)
- [Queries](#queries)
- [Unique constraint violations](#unique-constraint-violations)

## Boundary, concurrency, and transactions

- **INFRA-REPOSITORY-001** — Repository adapters translate domain repository contracts to data access, while persistence repositories expose data-access capability only.
- **INFRA-PERSISTENCE-001** — Keep concrete JPA, MyBatis, or MyBatis-Plus repositories, DOs, DO conversion mappers, mapper statements, and technical assembly in the matching persistence implementation module.
- **INFRA-DO-001** — Do not share DO classes across persistence technologies; each JPA, MyBatis, or MyBatis-Plus implementation module owns `*DO` types with only the mapping annotations required by that technology.
- **INFRA-MAPPER-001** — Each persistence implementation module owns the conversion mappers between domain objects or application read models and that technology's DOs; do not place converters that depend on concrete DOs in shared infrastructure.
- **PERSISTENCE-SELECTION-001** — Use one persistence technology per repository and select JPA, MyBatis, or MyBatis-Plus; a project may replace MyBatis-Plus with MyBatis when its license policy requires it.
- **PERSISTENCE-MYBATIS-COMPAT-001** — Replacing MyBatis-Plus with MyBatis must preserve the database schema, table and column names, query semantics, and observable repository behavior.
- **PERSISTENCE-MYBATIS-SQL-001** — MyBatis and MyBatis-Plus implementations must load the same shared SQL XML fragments from the project-root `config/` directory.
- **PERSISTENCE-MYBATIS-SQL-002** — Declare table names, column lists, and reusable predicates only in shared fragments; concrete mapper statements reference them with `<include>` and must not duplicate them across the two implementations.
- **PERSISTENCE-DO-PORTABILITY-001** — For the same logical persistence model, technology-specific JPA, MyBatis, and MyBatis-Plus DOs keep identical business-property names and Java types.
- **PERSISTENCE-DO-COLLECTION-001** — DOs must not declare collection properties or collection-mapping annotations such as `@ElementCollection` and `@CollectionTable`; model multi-value relationships as master/detail DOs and association tables.
- **PERSISTENCE-DO-COLLECTION-002** — Only when the multi-value content is always read and written as a whole and needs no independent query, index, or foreign-key constraint may each technology's DO conversion mapper serialize it into a single string column under consistent conversion rules.
- **PERSISTENCE-SCHEMA-001** — Define nullability, uniqueness, foreign keys, and indexes in schema migrations; application validation and distributed coordination do not replace database constraints.
- **PERSISTENCE-SCHEMA-LOCATION-001** — Prefer the project-root `config/` directory for `schema.sql` and database migration configuration and load it under the layered-service external-resource rules.
- **PERSISTENCE-SCHEMA-PREFIX-001** — `schema.sql` must begin with a comment requiring its table prefix and suffix to match runtime configuration; changing the prefix or suffix is a schema change that must update the DDL scripts or migration configuration, not only the runtime configuration.
- **PERSISTENCE-CONCURRENCY-SCENARIO-001** — Choose concurrency controls from actual contention and business risk; do not add versions, distributed locks, or pessimistic database locks to very-low-contention operational APIs for hypothetical races.
- **PERSISTENCE-CONCURRENCY-STATE-001** — When a business state machine or explicit precondition already exists, prefer a conditional update on the current state or business condition and treat an unexpected affected-row count as a state change or concurrency conflict.
- **PERSISTENCE-CONCURRENCY-001** — For real cross-instance conflicts not resolved by business conditions, prefer a distributed lock to reduce contention and add version-based optimistic concurrency only when residual conflicts still require detection; never make versions, pessimistic database locks, or distributed locks the default for every write.
- **PERSISTENCE-TRANSACTION-001** — Keep local-database transactions short and limited to necessary reads and writes; do not perform avoidable RPC, direct message publication, long computation, lock waiting, or multi-batch work inside them.

## Queries

- **PERSISTENCE-PAGE-001** — Paginate with a stable unique order and prefer cursors for deep or continuous scans; never rely on unspecified database order.
- **PERSISTENCE-QUERY-001** — Support frequent and large-table conditions, ordering, and joins with verified indexes and query plans; prohibit N+1 access and unbounded result sets.
- **PERSISTENCE-JPA-001** — Do not rely on Open Session in View to hide JPA lazy loading; design aggregate fetch boundaries and read projections explicitly.
- **PERSISTENCE-MYBATIS-001** — Configure the MyBatis-Plus database type explicitly and fail on unsupported types; keep custom interceptors ordered and verify they cannot bypass pagination or naming rules.

## Unique constraint violations

- **PERSISTENCE-DUPLICATE-001** — At the persistence-adapter boundary, translate a known business unique-constraint conflict into an explicit domain/application exception or, after verifying equivalent existing data, an idempotent success; do not leak the database exception.
- **PERSISTENCE-DATA-INTEGRITY-001** — A shared `DataIntegrityViolationException` catch may cover JPA and Spring-translated MyBatis `DuplicateKeyException`, which is a subclass of it.
- **PERSISTENCE-DATA-INTEGRITY-002** — After the shared catch, confirm the violation is the intended unique constraint by known constraint name, SQLState/vendor code, or a business-key reread; never classify not-null, foreign-key, or check-constraint violations as duplicate keys.
- **PERSISTENCE-JPA-FLUSH-001** — To catch a JPA unique-constraint failure inside an adapter, flush within that catch boundary; otherwise translate the deferred exception at a boundary that covers transaction commit.
- **PERSISTENCE-DUPLICATE-LOG-001** — Log every unique-constraint conflict once at the translation boundary with constraint/scene, sanitized business key, and outcome; use INFO or WARN for expected idempotency and ERROR plus rethrow for unknown or unclassified integrity failures.
