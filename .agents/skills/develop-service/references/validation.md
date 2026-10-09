# Baseline Validation and Exception Rules

## Contents

- [Bean Validation](#bean-validation)
- [Explicit checks](#explicit-checks)
- [Exceptions and logging](#exceptions-and-logging)

## Bean Validation

- **JAVA-BEAN-VALIDATION-PREFERENCE-001** **[DEFAULT][BASELINE]** — When dependencies and runtime validation are available, prefer Jakarta Bean Validation for nullability, format, and range constraints on bindable properties and method parameters or returns.
- **JAVA-BEAN-VALIDATION-EXECUTION-001** **[REQUIRED][BASELINE]** — When using Bean Validation, declare constraints at the corresponding field, property, record component, parameter, or return location and ensure the actual call path triggers validation.
- **JAVA-VALIDATION-CASCADE-001** **[REQUIRED][BASELINE]** — `@Valid` marks cascaded validation of nested objects and is not itself a constraint.

## Explicit checks

- **JAVA-VALIDATION-002** **[REQUIRED][BASELINE]** — Use explicit checks when Bean Validation cannot express the constraint, the call path cannot guarantee validation, or an explicit business-error meaning is required; this includes constructors and factories outside framework binding that need fast failure.
- **JAVA-VALIDATION-TECHNICAL-001** **[REQUIRED][BASELINE]** — Technical preconditions detect programming errors or protect internal technical invariants. They must not use service business error codes; general technical exceptions or `FrameworkException` with framework-wide technical codes are allowed.
- **JAVA-CHECK-TOOL-ORDER-001** **[REQUIRED][BASELINE]** — Choose explicit-check tools by failure semantics first, then available dependencies and capability, in order: framework-core `Require`, Spring `Assert`, managed Commons `Validate`, and JDK checks such as `Objects.requireNonNull`. Advance only when the preceding tool is unavailable or unsuitable; handwrite checks only when existing tools and the JDK cannot satisfy the requirement.
- **JAVA-VALIDATION-003** **[REQUIRED][BASELINE]** — Do not add dependencies solely for explicit checks or hand-write equivalent logic already provided by an available tool that satisfies the failure semantics; dependency selection follows the common-utilities rules.

## Exceptions and logging

- **JAVA-EXCEPTION-SEMANTICS-001** **[REQUIRED][BASELINE]** — Classify exceptions by failure semantics rather than merely inheritance from `BaseException`.
- **JAVA-EXCEPTION-LANGUAGE-001** **[REQUIRED][BASELINE]** — Service business errors use bound Chinese templates; project-authored technical diagnostics and Bean Validation messages use English. Original third-party messages are exempt.
- **JAVA-EXCEPTION-PROTOCOL-001** **[REQUIRED][BASELINE]** — Determine business outcomes by stable business error codes; never parse an exception message as a stable protocol.
- **JAVA-EXCEPTION-003** **[REQUIRED][BASELINE]** — Handle or propagate business exceptions and never silently ignore an exception; non-business exception logging follows the logging rules.
