# MapStruct Rules

## Conversion contracts

- **MAPSTRUCT-CONVERSION-001** **[REQUIRED][BASELINE]** — Use MapStruct for structural mappings between data carriers with corresponding fields; do not hand-write field copying or use `BeanUtils.copyProperties`.
- **MAPSTRUCT-SEMANTIC-001** **[REQUIRED][BASELINE]** — Use explicit semantic methods for domain construction, business validation, state transitions, and protocol encoding; a MapStruct mapper may call those methods but must not hide them in automatic field mapping.
- **MAPSTRUCT-CONTRACT-BINDING-001** **[REQUIRED][BASELINE]** — Define a conversion contract independently; a handwritten `@Mapper` interface extends it or abstract class implements it, using `@Override` when redeclaring contract methods. The handwritten mapper follows the naming rules.
- **MAPSTRUCT-GENERATED-001** **[REQUIRED][BASELINE]** — MapStruct generates concrete implementations at compile time; never handwrite or modify generated classes.

## Reuse and configuration

- **MAPSTRUCT-REUSE-001** **[REQUIRED][BASELINE]** — Reuse existing mappers through `@Mapper(uses = {...})` instead of duplicating field-level conversions.
- **MAPSTRUCT-SHARED-CONFIG-001** **[REQUIRED][BASELINE]** — Declare `componentModel = spring` and `unmappedTargetPolicy = ERROR` in shared `@MapperConfig`, reference it via each mapper's `config`, and retain these shared defaults.
- **MAPSTRUCT-EXPLICIT-IGNORE-001** **[REQUIRED][BASELINE]** — For ordinary mappings, explicitly declare `ignore = true` for each intentionally unmapped target field.
- **MAPSTRUCT-WHITELIST-001** **[REQUIRED][BASELINE]** — For partial-update or projection whitelists, use method-level `@BeanMapping(ignoreByDefault = true)` and explicit allowed mappings; never substitute local `unmappedTargetPolicy = IGNORE` or disable missing-mapping checks.
- **MAPSTRUCT-HELPER-001** **[REQUIRED][BASELINE]** — Keep generic converters and helpers private to mapper use and do not expose them as business components.
