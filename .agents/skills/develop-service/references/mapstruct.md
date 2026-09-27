# MapStruct Rules

## Conversion contracts

- **MAPSTRUCT-CONVERSION-001** **[REQUIRED][BASELINE]** — Use MapStruct for structural mappings between data carriers with corresponding fields; do not hand-write field copying or use `BeanUtils.copyProperties`.
- **MAPSTRUCT-SEMANTIC-001** **[REQUIRED][BASELINE]** — Use explicit semantic methods for domain construction, business validation, state transitions, and protocol encoding; a MapStruct mapper may call those methods but must not hide them in automatic field mapping.
- **MAPSTRUCT-CONTRACT-001** **[REQUIRED][BASELINE]** — Define the conversion contract independently; MapStruct is an implementation mechanism rather than the contract itself.
- **MAPSTRUCT-IMPLEMENTATION-001** **[REQUIRED][BASELINE]** — A handwritten `@Mapper` interface extends the independent conversion contract, or a `@Mapper` abstract class implements it; use `@Override` when redeclaring contract methods. MapStruct generates the concrete implementation: never handwrite or modify generated classes. Naming rules apply to the handwritten mapper type.

## Reuse and configuration

- **MAPSTRUCT-REUSE-001** **[REQUIRED][BASELINE]** — Reuse existing mappers through `@Mapper(uses = {...})` instead of duplicating field-level conversions.
- **MAPSTRUCT-CONFIG-001** **[REQUIRED][BASELINE]** — Declare `componentModel = spring` and `unmappedTargetPolicy = ERROR` in a shared `@MapperConfig` referenced by each mapper; explicitly ignore intentionally unmapped target fields. For partial-update or projection whitelists, use method-level `@BeanMapping(ignoreByDefault = true)` and explicitly declare allowed mappings. Never substitute local `unmappedTargetPolicy = IGNORE` for a whitelist or disable unmapped-field checks; retain the shared default.
- **MAPSTRUCT-HELPER-001** **[REQUIRED][BASELINE]** — Keep generic converters and helpers private to mapper use and do not expose them as business components.
