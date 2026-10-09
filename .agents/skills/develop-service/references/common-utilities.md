# Common Utility Rules

## Selection and reuse

- **JAVA-UTILITY-001** **[DEFAULT][BASELINE]** — Prefer the JDK, an already-used framework, or a mature maintained library over reimplementing general technical capabilities.
- **JAVA-PREDICATE-SEMANTICS-001** **[REQUIRED][BASELINE]** — Distinguish null-only, non-empty, and non-blank before selecting a predicate; never change semantics to standardize tools. Treat null and empty collections alike only when the scenario allows it.
- **JAVA-UTILITY-SPRING-001** **[REQUIRED][BASELINE]** — In modules already depending on Spring, use semantics-matching utilities: `StringUtils.hasLength` for non-empty strings, `hasText` for non-blank strings, and `CollectionUtils.isEmpty` when null and empty collections are equivalent. Use null comparisons or JDK equivalents for null-only checks.
- **JAVA-UTILITY-004** **[REQUIRED][BASELINE]** — A module decoupled from Spring must not add Spring Framework only for utility methods.
- **JAVA-UTILITY-005** **[REQUIRED][BASELINE]** — When the JDK is insufficient in a Spring-decoupled module, use managed Commons tools matching the predicate semantics: strings use `isEmpty`/`isNotEmpty` or `isBlank`/`isNotBlank`; collections use collections4 `isEmpty`/`isNotEmpty` only when null and empty are equivalent.
- **JAVA-UTILITY-006** **[REQUIRED][BASELINE]** — Do not hand-write logic equivalent to the utilities above.
