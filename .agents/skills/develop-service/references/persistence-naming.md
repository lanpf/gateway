# Persistence Naming Rules

## Shared SQL XML

- **NAME-MYBATIS-SQL-001** **[REQUIRED][TOPIC]** — Name shared MyBatis/MyBatis-Plus SQL fragment files `<Aggregate>SqlFragments.xml` with namespace `<service-package>.persistence.sql.<Aggregate>SqlFragments`.
- **NAME-MYBATIS-MAPPER-FILE-001** **[REQUIRED][TOPIC]** — Name concrete mapper XML files `<Aggregate>Mapper.xml`.
- **NAME-MYBATIS-REFID-001** **[REQUIRED][TOPIC]** — Reference shared SQL fragments under the persistence reuse rules and always use a fully qualified `refid`.

## Query naming

- **INFRA-QUERY-001** **[REQUIRED][TOPIC]** — Name persistence queries by conditions, range, and ordering rather than by a single caller or use case.
