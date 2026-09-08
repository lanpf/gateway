# Baseline Validation and Exception Rules

## Contents

- [Bean Validation](#bean-validation)
- [Explicit checks](#explicit-checks)
- [Exceptions and logging](#exceptions-and-logging)

## Bean Validation

- **JAVA-VALIDATION-001** **[DEFAULT][BASELINE]** — Prefer Jakarta Bean Validation for constraints on bindable Bean properties and method parameters or return values; declare nullability, format, and range constraints on the owning field, property, or record component.
- **JAVA-VALIDATION-CASCADE-001** **[REQUIRED][BASELINE]** — `@Valid` marks cascaded validation of nested objects and is not itself a constraint.

## Explicit checks

- **JAVA-VALIDATION-002** **[REQUIRED][BASELINE]** — Use explicit checks only when Bean Validation cannot express a constraint or an internal API outside the framework binding path requires fast failure, such as a domain constructor or factory.
- **JAVA-VALIDATION-TECHNICAL-001** **[REQUIRED][BASELINE]** — Technical preconditions detect programming errors or protect internal invariants and do not form a stable business protocol; their exception messages are diagnostic only and callers must not inspect message text to determine a business result.
- **JAVA-OBJECTS-REQUIRE-NONNULL-001** **[DEFAULT][BASELINE]** — For a technical precondition that checks nullity only, prefer JDK `Objects.requireNonNull`.
- **JAVA-ASSERT-001** **[REQUIRED][BASELINE]** — For a technical precondition that checks blankness, ranges, collections, or arbitrary conditions, a module already depending on Spring Framework for its responsibility uses `org.springframework.util.Assert`.
- **JAVA-VALIDATE-001** **[REQUIRED][BASELINE]** — For the same richer technical preconditions, a Spring-decoupled module uses project-managed `org.apache.commons.lang3.Validate`.
- **JAVA-VALIDATION-003** **[REQUIRED][BASELINE]** — Do not add Spring Framework only for explicit checks and do not hand-write equivalents these tools provide; utility dependency selection follows the common-utilities rules.
- **JAVA-VALIDATION-MESSAGE-001** **[REQUIRED][BASELINE]** — Jakarta Bean Validation and technical-precondition diagnostic messages are written in English and are not a stable business protocol.

## Exceptions and logging

- **JAVA-EXCEPTION-003** **[REQUIRED][BASELINE]** — Handle or propagate business exceptions and never silently ignore an exception; non-business exception logging follows the logging rules.
