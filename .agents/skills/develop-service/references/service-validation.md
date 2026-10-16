# Layered Service Validation and Exception Rules

## Contents

- [Bean Validation](#bean-validation)
- [Explicit checks](#explicit-checks)
- [Exceptions and logging](#exceptions-and-logging)

## Bean Validation

- **JAVA-VALIDATION-ENTRY-GROUP-001** **[REQUIRED][TOPIC]** — Use `@Valid` for request-object entries with the default validation group and `@Validated` for explicitly selected groups; never stack both indiscriminately at the same entry.
- **JAVA-VALIDATION-CONTRACT-001** **[DEFAULT][TOPIC]** — Prefer declaring public API method-parameter and return-value constraints on the API interface or API data type.
- **JAVA-VALIDATION-CONTRACT-002** **[REQUIRED][TOPIC]** — Implementations must not duplicate or strengthen parameter constraints declared by a public API interface.
- **JAVA-MVC-OBJECT-VALIDATION-001** **[REQUIRED][TOPIC]** — Trigger Spring MVC request-object validation through entry-parameter `@Valid`, or `@Validated` for specific groups; follow the entry group-selection rules.
- **JAVA-MVC-METHOD-VALIDATION-001** **[REQUIRED][TOPIC]** — Direct MVC parameter constraints such as `@NotBlank` or `@Min` use MVC method validation; do not blanket-annotate Controllers with class-level `@Validated` merely to enable it.
- **JAVA-VALIDATION-SPRING-BEAN-001** **[REQUIRED][TOPIC]** — For method-parameter or return-value validation on a regular Spring Bean such as a Facade or application service, put `@Validated` on the concrete Bean implementation and invoke it through the Spring proxy; the interface declares contract constraints and the implementation enables runtime method validation.

## Explicit checks

- **JAVA-VALIDATION-REQUIRE-001** **[REQUIRED][TOPIC]** — Business checks express expected business rejections and must throw an exception extending framework-core `BaseException` with a stable business code, through a Require exception supplier or an explicit throw. Tool fallback must preserve that meaning; ordinary technical preconditions must not use business exception suppliers.
- **JAVA-REQUIRE-VALID-PURITY-001** **[REQUIRED][TOPIC]** — A `Require.valid` predicate uses its passed `value` as the sole input: it may call value methods, reference `static final` constants or class literals, and use immutable values derived solely from `value` inside the predicate. Never capture other variables, parameters, instance fields, or mutable external state.
- **JAVA-CROSS-VARIABLE-GUARD-001** **[REQUIRED][TOPIC]** — Use explicit guards for cross-variable checks, never `Require.valid`; classify their exceptions by failure semantics rather than treating every cross-variable decision as a technical precondition.
- **JAVA-REQUIRE-NONNULL-SCOPE-001** **[REQUIRED][TOPIC]** — Use `Require.notNull`/`allNotNull` only for constructor parameters and public-method parameters, not return values, local variables, or fields.
- **JAVA-REQUIRE-NONNULL-BATCH-001** **[REQUIRED][TOPIC]** — For Require parameter null checks, use `notNull` for one parameter and `allNotNull` for multiple parameters; do not chain repeated `notNull` calls.
- **JAVA-COLLECTION-NULL-EXCEPTION-001** **[REQUIRED][TOPIC]** — Technical null-element checks cover collection elements and both Map keys and values; any null element throws `FrameworkException`. This rule does not replace domain or business validity checks.
- **JAVA-REQUIRE-NO-NULLS-SELECTION-001** **[DEFAULT][TOPIC]** — In modules already depending on framework-core, prefer `Require.noNulls` when only collection or Map null-element checking is needed. It rejects null containers, allows empty containers, and checks both Map keys and values. Its exception-supplier overload uses the supplied exception for null containers, elements, keys, and values; business checks follow the business-exception rule.
- **JAVA-NULLSAFE-COPY-SELECTION-001** **[DEFAULT][TOPIC]** — In modules already depending on framework-core, prefer NullSafe.copyToList, copyToSet, or copyToMap for an immutable container copy only when the contract permits null and empty containers, treats them equivalently, and allows technical exceptions. These methods already check elements for null, so do not add a redundant Require.noNulls check; they do not accept a custom exception supplier.
- **JAVA-CONTAINER-NULL-SEMANTICS-001** **[REQUIRED][TOPIC]** — Distinguish null containers, empty containers, and null elements by contract. Use NullSafe collection or Map APIs, including traversal, normalization, and copying, only when null and empty are both permitted and semantically equivalent. Never use NullSafe when the input must be non-null or non-empty, or an upstream contract or object invariant already guarantees a non-null container; a preceding check does not make it acceptable. Traverse or copy guaranteed non-null containers directly. Enforce non-emptiness separately when at least one element is required; null-element checks do not establish it.
- **JAVA-CONSTRUCTOR-CONTAINER-NULLABILITY-001** **[REQUIRED][TOPIC]** — Constructor collection and Map parameters are non-null by default; allowing an empty container does not imply allowing null. Document each nullable parameter in the constructor Javadoc @param with its null-handling semantics and any conditions under which null is permitted. Never implicitly widen null acceptance by using NullSafe.
- **JAVA-VALIDATION-API-001** **[DEFAULT][TOPIC]** — Prefer Jakarta Bean Validation for API field-shape constraints; a check returns a business error only when the API contract explicitly promises that error semantics. Execute the check at its responsibility boundary rather than moving it into API or protocol code because the API exposes the error.
- **JAVA-VALIDATION-LAYER-001** **[REQUIRED][TOPIC]** — Validate rules that depend on domain state, persistence data, or use-case context in application or domain rather than moving them to the protocol-binding layer.

## Exceptions and logging

- **JAVA-BEAN-VALIDATION-EXCEPTION-001** **[REQUIRED][TOPIC]** — Translate Jakarta Bean Validation failures into the framework exception hierarchy at the unified exception translation boundary.
- **JAVA-BUSINESS-EXCEPTION-BASE-001** **[REQUIRED][TOPIC]** — Service-defined business exceptions extend framework-core `BaseException`, never JDK `RuntimeException` or other JDK exceptions directly.
- **JAVA-DOMAIN-GUARD-001** **[REQUIRED][TOPIC]** — Guards expressing domain rules or domain validity throw `DomainException` extending `BaseException`, not generic JDK exceptions. Programming errors and technical preconditions inside domain remain technical exceptions; location alone does not make them business failures.
- **JAVA-DOMAIN-EXCEPTION-FACTORY-001** **[REQUIRED][TOPIC]** — Each domain's `DomainException` provides `invalidEntityId()` for invalid domain entity identifiers. The error-code policy exclusively defines the code allocation for `DOMAIN_ENTITY_ID_INVALID`.
