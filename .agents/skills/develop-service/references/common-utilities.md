# Common Utility Rules

## Selection and reuse

- **JAVA-UTILITY-001** **[DEFAULT][BASELINE]** — Prefer the JDK, an already-used framework, or a mature maintained library over reimplementing general technical capabilities.
- **JAVA-UTILITY-002** **[REQUIRED][BASELINE]** — Distinguish null-only, non-empty (non-null and nonzero length), and non-blank (contains a non-whitespace character) before selecting a predicate; never change semantics to standardize tools. In Spring-dependent modules use `org.springframework.util.StringUtils.hasLength` for non-empty and `hasText` for non-blank, and `org.springframework.util.CollectionUtils.isEmpty` only when null and empty collections are equivalent. Use `== null` / `!= null` or JDK equivalents for null-only checks and other Spring utilities only when their semantics match.
- **JAVA-UTILITY-004** **[REQUIRED][BASELINE]** — A module decoupled from Spring must not add Spring Framework only for utility methods.
- **JAVA-UTILITY-005** **[REQUIRED][BASELINE]** — In Spring-decoupled modules distinguish the same predicate semantics first. When the JDK is insufficient, use managed Apache Commons `org.apache.commons.lang3.StringUtils.isEmpty`/`isNotEmpty` for empty/non-empty and `isBlank`/`isNotBlank` for blank/non-blank, and `org.apache.commons.collections4.CollectionUtils.isEmpty`/`isNotEmpty` only when null and empty collections are equivalent; never interchange empty and blank.
- **JAVA-UTILITY-006** **[REQUIRED][BASELINE]** — Do not hand-write logic equivalent to the utilities above.
