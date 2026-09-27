# Baseline Validation and Exception Rules

## Contents

- [Bean Validation](#bean-validation)
- [Explicit checks](#explicit-checks)
- [Exceptions and logging](#exceptions-and-logging)

## Bean Validation

- **JAVA-VALIDATION-001** **[DEFAULT][BASELINE]** — When dependencies and runtime validation are available, prefer Jakarta Bean Validation for nullability, format, and range constraints on bindable Bean properties and method parameters or returns. Declare constraints on the corresponding field, property, record component, parameter, or return location and ensure the call path actually triggers validation.
- **JAVA-VALIDATION-CASCADE-001** **[REQUIRED][BASELINE]** — `@Valid` marks cascaded validation of nested objects and is not itself a constraint.

## Explicit checks

- **JAVA-VALIDATION-002** **[REQUIRED][BASELINE]** — Use explicit checks when Bean Validation cannot express the constraint, the call path cannot guarantee validation, or an explicit business-error meaning is required; this includes constructors and factories outside framework binding that need fast failure.
- **JAVA-VALIDATION-TECHNICAL-001** **[REQUIRED][BASELINE]** — Technical preconditions detect programming errors or protect internal technical invariants. They must not use service business error codes; general technical exceptions or `FrameworkException` with framework-wide technical codes are allowed.
- **JAVA-OBJECTS-REQUIRE-NONNULL-001** **[REQUIRED][BASELINE]** — Use JDK checks such as `Objects.requireNonNull` when existing Require, Assert, and Validate dependencies are unavailable or cannot satisfy the constraint and failure semantics. Hand-write a check only when existing tools and the JDK cannot meet the need.
- **JAVA-REQUIRE-PRIORITY-001** **[REQUIRED][BASELINE]** — Select explicit-check tools by failure semantics first, then existing dependencies and capability; prefer framework-core `Require` when it can meet both the constraint and exception semantics.
- **JAVA-ASSERT-001** **[REQUIRED][BASELINE]** — Use Spring `org.springframework.util.Assert` from an existing dependency when Require is unavailable or unsuitable and Assert satisfies the constraint and failure semantics.
- **JAVA-VALIDATE-001** **[REQUIRED][BASELINE]** — Use project-managed `org.apache.commons.lang3.Validate` from an existing dependency when Require and Assert are unavailable or unsuitable and Validate satisfies the constraint and failure semantics.
- **JAVA-VALIDATION-003** **[REQUIRED][BASELINE]** — Do not add dependencies solely for explicit checks or hand-write equivalent logic already provided by an available tool that satisfies the failure semantics; dependency selection follows the common-utilities rules.

## Exceptions and logging

- **JAVA-VALIDATION-MESSAGE-001** **[REQUIRED][BASELINE]** — Classify exceptions by failure semantics, not merely inheritance from `BaseException`. Service business errors use the Chinese templates bound to their error codes; project-authored technical diagnostics and Bean Validation messages use English, while original third-party exception messages are exempt. Callers use business error codes, never exception message parsing, to determine business outcomes.
- **JAVA-EXCEPTION-003** **[REQUIRED][BASELINE]** — Handle or propagate business exceptions and never silently ignore an exception; non-business exception logging follows the logging rules.
