# Lombok Rules

## General use

- **LOMBOK-EXPERIMENTAL-001** **[REQUIRED][BASELINE]** — Do not use annotations from `lombok.experimental`.
- **LOMBOK-BUILDER-001** **[REQUIRED][BASELINE]** — Do not use `@Builder`; use explicit constructors or factory methods for staged or fluent construction.
- **LOMBOK-CONSTRUCTOR-001** **[REQUIRED][BASELINE]** — Generate constructors that only assign fields and contain no validation, transformation, or initialization logic with Lombok constructor annotations rather than writing them manually; write an explicit constructor when such logic exists.
- **LOMBOK-ACCESSOR-001** **[REQUIRED][BASELINE]** — Generate getters and setters that only read or assign fields and contain no validation, transformation, or other behavior with Lombok `@Getter` or `@Setter`; write methods explicitly when behavior exists.
- **LOMBOK-LOG-001** **[REQUIRED][BASELINE]** — Use `@Slf4j` instead of declaring a Logger field manually.

## Type-specific use

- **LOMBOK-ENTITY-001** **[REQUIRED][BASELINE]** — Aggregate roots and entities use only `@Getter`, avoid `@Data` and Lombok `@EqualsAndHashCode`, and implement equality explicitly from the unique identifier.
- **LOMBOK-VALUE-001** **[REQUIRED][BASELINE]** — When a value object is an ordinary class, use final fields, getters, an appropriate all-arguments constructor, and full-field equality.
- **LOMBOK-SPRING-001** **[REQUIRED][BASELINE]** — Spring Beans use final dependencies and constructor injection, normally through `@RequiredArgsConstructor`.
