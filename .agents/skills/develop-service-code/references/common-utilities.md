# Common Utility Rules

## Selection and reuse

- **JAVA-UTILITY-001** — Prefer the JDK, an already-used framework, or a mature maintained library over reimplementing general technical capabilities.
- **JAVA-UTILITY-002** — When a module already depends on Spring Framework for its responsibility, use `org.springframework.util.StringUtils.hasText` for string null/empty/blank checks, `org.springframework.util.CollectionUtils.isEmpty` for collection checks, and matching `org.springframework.util` utilities for other basic checks.
- **JAVA-UTILITY-004** — A module decoupled from Spring must not add Spring Framework only for utility methods.
- **JAVA-UTILITY-005** — When the JDK is insufficient for a Spring-decoupled module, use project-managed Apache Commons: `org.apache.commons.lang3.StringUtils.isBlank`/`isNotBlank` for strings and `org.apache.commons.collections4.CollectionUtils.isEmpty`/`isNotEmpty` for collections.
- **JAVA-UTILITY-006** — Do not hand-write logic equivalent to the utilities above.
- **JAVA-UTILITY-003** — Before adding a library, evaluate maintenance activity, security, license, and dependency cost; do not add overlapping functionality when existing dependencies suffice.
