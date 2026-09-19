# MapStruct Rules

## Conversion contracts

- **MAPSTRUCT-CONVERSION-001** **[REQUIRED][BASELINE]** — Use MapStruct for structural mappings between data carriers with corresponding fields; do not hand-write field copying or use `BeanUtils.copyProperties`.
- **MAPSTRUCT-SEMANTIC-001** **[REQUIRED][BASELINE]** — Use explicit semantic methods for domain construction, business validation, state transitions, and protocol encoding; a MapStruct mapper may call those methods but must not hide them in automatic field mapping.
- **MAPSTRUCT-CONTRACT-001** **[REQUIRED][BASELINE]** — Define the conversion contract independently; MapStruct is an implementation mechanism rather than the contract itself.
- **MAPSTRUCT-IMPLEMENTATION-001** **[REQUIRED][BASELINE]** — MapStruct implementation classes implement the independent conversion contract and annotate implemented methods with `@Override`; naming is governed by the service naming rules.

## Reuse and configuration

- **MAPSTRUCT-REUSE-001** **[REQUIRED][BASELINE]** — Reuse existing mappers through `@Mapper(uses = {...})` instead of duplicating field-level conversions.
- **MAPSTRUCT-CONFIG-001** **[REQUIRED][BASELINE]** — Declare `componentModel = spring` and `unmappedTargetPolicy = ERROR` in a shared `@MapperConfig`; reference it from each mapper and explicitly mark intentionally unmapped target fields with `ignore = true`. Partial updates and projections may use local `ignoreByDefault = true` or `IGNORE` for whitelist mappings, but must not change the shared default.
- **MAPSTRUCT-HELPER-001** **[REQUIRED][BASELINE]** — Keep generic converters and helpers private to mapper use and do not expose them as business components.
