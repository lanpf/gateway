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

- **JAVA-VALIDATION-REQUIRE-001** **[REQUIRED][TOPIC]** — A business guard represents an expected business rejection that needs a stable error code; use framework-core `Require` and explicitly supply an exception extending `BaseException`. Do not replace a business exception with `Objects.requireNonNull`, `Validate`, or `Assert`, and do not use `Require` for an ordinary technical precondition.
- **JAVA-VALIDATION-API-001** **[DEFAULT][TOPIC]** — For API requests, prefer Jakarta Bean Validation for field-shape constraints and use `Require` in API only when the failure is a business error explicitly promised by the API contract.
- **JAVA-VALIDATION-LAYER-001** **[REQUIRED][TOPIC]** — Validate rules that depend on domain state, persistence data, or use-case context in application or domain rather than moving them to the protocol-binding layer.

## Exceptions and logging

- **JAVA-EXCEPTION-001** **[REQUIRED][TOPIC]** — Use the framework exception hierarchy for Jakarta Bean Validation failures.
- **JAVA-EXCEPTION-002** **[REQUIRED][TOPIC]** — A service-defined business exception extends framework-core `BaseException` and must not directly extend `RuntimeException` or another JDK exception type.
- **JAVA-DOMAIN-GUARD-001** **[REQUIRED][TOPIC]** — A domain-layer null check, precondition, or other guard throws `DomainException`, which extends `BaseException`; do not substitute `IllegalArgumentException`, `IllegalStateException`, or another generic exception.
- **JAVA-DOMAIN-EXCEPTION-FACTORY-001** **[REQUIRED][TOPIC]** — `DomainException` provides the static factory methods `invalidEntityId()` and `missingField()`.
