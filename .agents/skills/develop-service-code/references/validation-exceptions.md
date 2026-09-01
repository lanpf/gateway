# Validation and Exception Rules

## Validation

- **JAVA-VALIDATION-001** — Use Jakarta Bean Validation for constraints on bindable Bean properties and method parameters.
- **JAVA-VALIDATION-002** — Use explicit checks only when framework validation cannot express a constraint or an internal API outside the framework binding path requires fast failure.
- **JAVA-ASSERT-001** — For technical or programming preconditions that need no business exception, a module already depending on Spring Framework for its responsibility uses `org.springframework.util.Assert`.
- **JAVA-VALIDATE-001** — A Spring-decoupled module uses project-managed `org.apache.commons.lang3.Validate` for the same technical preconditions.
- **JAVA-VALIDATION-003** — Do not add Spring Framework only for explicit checks and do not hand-write equivalents these tools provide; utility dependency selection follows the common-utilities rules.
- **JAVA-VALIDATION-REQUIRE-001** — Use framework-core `Require` for an explicit check that must throw a `BaseException`, especially a domain-layer guard.
- **JAVA-VALIDATION-MESSAGE-001** — Validation failure exception messages must be written in English and contain no Chinese text.

## Exceptions and logging

- **JAVA-EXCEPTION-001** — Use the framework exception hierarchy for Jakarta Bean Validation failures.
- **JAVA-EXCEPTION-002** — A service-defined business exception extends framework-core `BaseException` and must not directly extend `RuntimeException` or another JDK exception type.
- **JAVA-DOMAIN-GUARD-001** — A domain-layer null check, precondition, or other guard throws `DomainException`, which extends `BaseException`; do not substitute `IllegalArgumentException`, `IllegalStateException`, or another generic exception.
- **JAVA-DOMAIN-EXCEPTION-FACTORY-001** — `DomainException` provides the static factory methods `invalidEntityId()` and `missingField()`.
- **JAVA-EXCEPTION-003** — Handle or propagate business exceptions; log non-business exceptions with enough context to locate the problem, and never silently ignore them.
