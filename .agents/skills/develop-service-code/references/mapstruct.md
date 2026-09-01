# MapStruct Rules

## Conversion contracts

- **MAPSTRUCT-CONVERSION-001** — Use MapStruct for object conversion; do not hand-write field copying or use `BeanUtils.copyProperties`.
- **MAPSTRUCT-CONTRACT-001** — Define the conversion contract independently; MapStruct is an implementation mechanism rather than the contract itself.
- **MAPSTRUCT-IMPLEMENTATION-001** — MapStruct implementation classes implement the independent conversion contract and annotate implemented methods with `@Override`; naming is governed by the service naming rules.

## Reuse and configuration

- **MAPSTRUCT-REUSE-001** — Reuse existing mappers through `@Mapper(uses = {...})` instead of duplicating field-level conversions.
- **MAPSTRUCT-CONFIG-001** — Declare `componentModel = spring` and `unmappedTargetPolicy = IGNORE` in a shared `@MapperConfig`; individual mappers reference that configuration.
- **MAPSTRUCT-HELPER-001** — Keep generic converters and helpers private to mapper use and do not expose them as business components.
