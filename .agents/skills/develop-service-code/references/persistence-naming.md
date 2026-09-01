# Persistence Naming Rules

## Shared SQL XML

- **NAME-MYBATIS-SQL-001** — Name shared MyBatis/MyBatis-Plus SQL fragment files `<Aggregate>SqlFragments.xml` with namespace `<service-package>.persistence.sql.<Aggregate>SqlFragments`.
- **NAME-MYBATIS-MAPPER-001** — Name concrete mapper XML files `<Aggregate>Mapper.xml` and reference shared `<sql>` elements with fully qualified `refid`.

## Query naming

- **INFRA-QUERY-001** — Name persistence queries by conditions, range, and ordering rather than by a single caller or use case.
