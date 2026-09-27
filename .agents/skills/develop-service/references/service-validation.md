# Layered Service Validation and Exception Rules

## Contents

- [Bean Validation](#bean-validation)
- [Explicit checks](#explicit-checks)
- [Exceptions and logging](#exceptions-and-logging)

## Bean Validation

- **JAVA-VALIDATION-CONTRACT-001** **[DEFAULT][TOPIC]** — Prefer declaring public API method-parameter and return-value constraints on the API interface or API data type.
- **JAVA-VALIDATION-CONTRACT-002** **[REQUIRED][TOPIC]** — Implementations must not duplicate or strengthen parameter constraints declared by a public API interface.
- **JAVA-VALIDATION-SPRING-MVC-001** **[REQUIRED][TOPIC]** — Trigger Spring MVC request-object validation with `@Valid` on the entry parameter, or `@Validated` when validation groups are required. Direct method-parameter constraints such as `@NotBlank` or `@Min` use Spring MVC method validation; do not add class-level `@Validated` to every Controller merely to enable it.
- **JAVA-VALIDATION-SPRING-BEAN-001** **[REQUIRED][TOPIC]** — For method-parameter or return-value validation on a regular Spring Bean such as a Facade or application service, put `@Validated` on the concrete Bean implementation and invoke it through the Spring proxy; the interface declares contract constraints and the implementation enables runtime method validation.

## Explicit checks

- **JAVA-VALIDATION-REQUIRE-001** **[REQUIRED][TOPIC]** — Business checks express expected business rejections and must throw an exception extending framework-core `BaseException` with a stable business code, through a Require exception supplier or an explicit throw. Tool fallback must preserve that meaning; ordinary technical preconditions must not use business exception suppliers.
- **JAVA-VALIDATION-COLLECTION-ELEMENT-001** **[DEFAULT][TOPIC]** — In modules depending on framework-core, prefer `FrameworkException.missingCollectionElement()` or `FrameworkException.invalidCollectionElement()` for technical checks of null or invalid collection elements.
- **JAVA-VALIDATION-API-001** **[DEFAULT][TOPIC]** — Prefer Jakarta Bean Validation for API field-shape constraints; a check returns a business error only when the API contract explicitly promises that error semantics. Execute the check at its responsibility boundary rather than moving it into API or protocol code because the API exposes the error.
- **JAVA-VALIDATION-LAYER-001** **[REQUIRED][TOPIC]** — Validate rules that depend on domain state, persistence data, or use-case context in application or domain rather than moving them to the protocol-binding layer.

## Exceptions and logging

- **JAVA-EXCEPTION-001** **[REQUIRED][TOPIC]** — Use the framework exception hierarchy for Jakarta Bean Validation failures.
- **JAVA-EXCEPTION-002** **[REQUIRED][TOPIC]** — A service-defined business exception extends framework-core `BaseException` and must not directly extend `RuntimeException` or another JDK exception type.
- **JAVA-DOMAIN-GUARD-001** **[REQUIRED][TOPIC]** — Guards expressing domain rules or domain validity throw `DomainException` extending `BaseException`, not generic JDK exceptions. Programming errors and technical preconditions inside domain remain technical exceptions; location alone does not make them business failures.
- **JAVA-DOMAIN-EXCEPTION-FACTORY-001** **[REQUIRED][TOPIC]** — Each domain's `DomainException` provides `invalidEntityId()` for invalid domain entity identifiers. The error-code policy exclusively defines the code allocation for `DOMAIN_ENTITY_ID_INVALID`.
