# Persistence Rules

## Contents

- [Boundary, concurrency, and transactions](#boundary,-concurrency,-and-transactions)
- [Queries](#queries)
- [Unique constraint violations](#unique-constraint-violations)

## Boundary, concurrency, and transactions

- **INFRA-REPOSITORY-001** **[REQUIRED][TOPIC]** — Repository adapters translate domain repository contracts to data access, while persistence repositories expose data-access capability only.
- **INFRA-PERSISTENCE-001** **[REQUIRED][TOPIC]** — The matching persistence implementation module owns concrete JPA, MyBatis, or MyBatis-Plus repositories, DOs, DO conversion mappers, and SQL/XML loading, assembly, and verification. Physical resource placement follows the layered-service configuration rules.
- **INFRA-DO-001** **[REQUIRED][TOPIC]** — Do not share DO classes across persistence technologies; each JPA, MyBatis, or MyBatis-Plus implementation module owns its DOs with only that technology's required mapping annotations, and DO names follow the naming rules.
- **INFRA-MAPPER-001** **[REQUIRED][TOPIC]** — Each persistence implementation module owns the conversion mappers between domain objects or application read models and that technology's DOs; do not place converters that depend on concrete DOs in shared infrastructure.
- **PERSISTENCE-SELECTION-001** **[REQUIRED][TOPIC]** — Use one persistence technology in each persistence implementation module and assemble only one implementation of a repository in a runtime artifact; alternative JPA, MyBatis, and MyBatis-Plus implementations may live in separate modules.
- **PERSISTENCE-MYBATIS-COMPAT-001** **[REQUIRED][TOPIC]** — Replacing MyBatis-Plus with MyBatis must preserve the database schema, table and column names, query semantics, and observable repository behavior.
- **PERSISTENCE-MYBATIS-SQL-001** **[REQUIRED][TOPIC]** — MyBatis and MyBatis-Plus implementations must load the same shared SQL XML fragments from the project-root `config/` directory.
- **PERSISTENCE-MYBATIS-SQL-002** **[REQUIRED][TOPIC]** — Declare table names, column lists, and reusable predicates only in shared fragments; concrete mapper statements reference them with `<include>` and must not duplicate them across the two implementations.
- **PERSISTENCE-DO-PORTABILITY-001** **[REQUIRED][TOPIC]** — For the same logical persistence model, technology-specific JPA, MyBatis, and MyBatis-Plus DOs keep identical business-property names and Java types.
- **PERSISTENCE-DO-COLLECTION-001** **[REQUIRED][TOPIC]** — DOs must not declare collection properties or collection-mapping annotations such as `@ElementCollection` and `@CollectionTable`; model multi-value relationships as master/detail DOs and association tables.
- **PERSISTENCE-DO-COLLECTION-002** **[REQUIRED][TOPIC]** — Only when the multi-value content is always read and written as a whole and needs no independent query, index, or foreign-key constraint may each technology's DO conversion mapper serialize it into a single string column under consistent conversion rules.
- **PERSISTENCE-TABLE-NAMING-001** **[REQUIRED][TOPIC]** — A persistence mapping may declare a logical table name without runtime prefix or suffix; resolve the physical prefix and suffix through the shared naming strategy and never hard-code a deployment-specific physical table name in business code.
- **PERSISTENCE-SCHEMA-001** **[REQUIRED][TOPIC]** — Enforce primary keys, business uniqueness, and database-expressible nullability, referential-integrity, and range invariants with schema constraints; application validation and distributed coordination do not replace those constraints, while cross-aggregate, cross-service, and temporal rules remain in domain or application.
- **PERSISTENCE-SCHEMA-LOCATION-001** **[DEFAULT][TOPIC]** — Prefer the project-root `config/` directory for `schema.sql` and database migration configuration and load it under the layered-service external-resource rules.
- **PERSISTENCE-SCHEMA-PREFIX-001** **[REQUIRED][TOPIC]** — `schema.sql` must begin with a comment requiring its table prefix and suffix to match runtime configuration; changing the prefix or suffix is a schema change that must update the DDL scripts or migration configuration, not only the runtime configuration.
- **PERSISTENCE-CONCURRENCY-SCENARIO-001** **[REQUIRED][TOPIC]** — Before choosing a concurrency control, assess business risk, actual conflict probability, contention scope, and failure consequences.
- **PERSISTENCE-CONCURRENCY-LOW-001** **[DEFAULT][TOPIC]** — For a very-low-contention operational API, omit versions, distributed locks, and pessimistic database locks by default.
- **PERSISTENCE-CONCURRENCY-STATE-001** **[DEFAULT][TOPIC]** — When a business state machine or explicit precondition exists, prefer a conditional update on the current state or business condition.
- **PERSISTENCE-CONCURRENCY-AFFECTED-ROWS-001** **[REQUIRED][TOPIC]** — Treat an unexpected conditional-update affected-row count as a state change or concurrency conflict; never overwrite silently.
- **PERSISTENCE-CONCURRENCY-001** **[DEFAULT][TOPIC]** — For a real cross-instance conflict not resolved by business conditions, prefer a distributed lock to reduce contention.
- **PERSISTENCE-CONCURRENCY-VERSION-001** **[DEFAULT][TOPIC]** — Add version-based optimistic concurrency only when residual conflicts still require detection.
- **PERSISTENCE-CONCURRENCY-TEMPLATE-001** **[REQUIRED][TOPIC]** — Never apply versions, pessimistic database locks, or distributed locks as a default template for every write.
- **PERSISTENCE-TRANSACTION-001** **[REQUIRED][TOPIC]** — Keep local-database transactions short and limited to necessary reads and writes; do not perform avoidable RPC, direct message publication, long computation, lock waiting, or multi-batch work inside them.
- **PERSISTENCE-RETRY-001** **[REQUIRED][TOPIC]** — Make every retried write idempotent, classify unique-constraint and optimistic-lock conflicts, deadlocks, and transient connection failures explicitly, and never retry without a bound.

## Queries

- **PERSISTENCE-PAGE-001** **[REQUIRED][TOPIC]** — Paginate with a stable unique order and never rely on unspecified database order.
- **PERSISTENCE-CURSOR-001** **[DEFAULT][TOPIC]** — Prefer cursors for deep pagination or continuous scans.
- **PERSISTENCE-QUERY-001** **[REQUIRED][TOPIC]** — Design indexes by access patterns, data scale, and execution plans; verify plans and acceptable performance before adding high-frequency or large-table queries rather than requiring an index per predicate. Prohibit N+1 access and unbounded result sets.
- **PERSISTENCE-JPA-001** **[REQUIRED][TOPIC]** — Do not rely on Open Session in View to hide JPA lazy loading; design aggregate fetch boundaries and read projections explicitly.
- **PERSISTENCE-MYBATIS-001** **[REQUIRED][TOPIC]** — Configure the MyBatis-Plus database type explicitly and fail on unsupported types; keep custom interceptors ordered and verify they cannot bypass pagination or naming rules.

## Unique constraint violations

- **PERSISTENCE-DUPLICATE-001** **[REQUIRED][TOPIC]** — At the persistence-adapter boundary, translate a known business unique-constraint conflict into an explicit domain/application exception or, after verifying equivalent existing data, an idempotent success; do not leak the database exception.
- **PERSISTENCE-DATA-INTEGRITY-001** **[ADVISORY][TOPIC]** — A shared `DataIntegrityViolationException` catch may cover JPA and Spring-translated MyBatis `DuplicateKeyException`, which is a subclass of it.
- **PERSISTENCE-DATA-INTEGRITY-002** **[REQUIRED][TOPIC]** — After the shared catch, confirm the violation is the intended unique constraint by known constraint name, SQLState/vendor code, or a business-key reread; never classify not-null, foreign-key, or check-constraint violations as duplicate keys.
- **PERSISTENCE-JPA-FLUSH-001** **[REQUIRED][TOPIC]** — To catch a JPA unique-constraint failure inside an adapter, flush within that catch boundary; otherwise translate the deferred exception at a boundary that covers transaction commit.
- **PERSISTENCE-IDEMPOTENT-RECOVERY-001** **[REQUIRED][TOPIC]** — After a unique-constraint failure, never reread and report idempotent success using a failed or rollback-only transaction or an unreliable persistence context. Especially after JPA/Hibernate persistence exceptions, end the failed transaction before verifying existing outcomes through a valid new transaction or independent query boundary. A nested call inside the failed transaction alone is not recovery; propagate verification failures.
- **PERSISTENCE-DUPLICATE-LOG-001** **[REQUIRED][TOPIC]** — Record unique-constraint conflicts once at the translation boundary with constraint/scene, sanitized business key, and outcome. Severity, recurring-event aggregation, and audit exceptions follow the logging rules; rethrow unknown or unclassified integrity failures.
