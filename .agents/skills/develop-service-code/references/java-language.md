# Java Language Rules

## Language and data design

- **JAVA-VERSION-002** — Compile and run with Java 17.
- **JAVA-VAR-001** — Use `var` for a local variable only when its initializer itself makes the concrete type clear; use an explicit type when understanding it requires reading a called method declaration or relying on generic type inference.
- **JAVA-RECORD-001** — Prefer `record` for data carriers whose state is complete at construction, remains immutable, and does not require inheritance, proxies, or JavaBean setter binding.
- **JAVA-RECORD-002** — Defensively copy collection record components with `List.copyOf`, `Set.copyOf`, or the corresponding immutable-copy operation.
- **JAVA-CLASS-001** — Use an ordinary class when the type requires inheritance, mutable state, framework proxying, JavaBean shape, or complex domain behavior.
- **JAVA-TYPE-001** — Prefer wrapper types such as `Integer`, `Long`, and `Boolean`; use primitives only for intentional default values, performance characteristics, or explicitly non-null semantics.

## Readable modern Java

- **JAVA-STYLE-001** — Prefer `forEach` for simple collection traversal and Stream for simple transformation, filtering, and mapping.
- **JAVA-STYLE-002** — Prefer lambdas and method references for simple callbacks.
- **JAVA-OPTIONAL-001** — Use `Optional` for possibly absent return values and chained handling; do not use it as a Bean property or method parameter.
- **JAVA-READABILITY-001** — For complex business rules, nested conditions, and exception flows, prioritize readability over functional style.
