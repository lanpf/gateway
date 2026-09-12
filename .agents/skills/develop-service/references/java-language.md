# Java Language Rules

## Language and data design

- **JAVA-VERSION-002** **[REQUIRED][BASELINE]** — Compile and run with Java 17.
- **JAVA-VAR-001** **[REQUIRED][BASELINE]** — Production code must not use `var`, including local variables, loop variables, try-with-resources variables, and lambda parameters; use an explicit type wherever a type declaration is needed.
- **JAVA-VAR-002** **[REQUIRED][BASELINE]** — Only test source sets may use `var`, and only when the initializer itself clearly shows the concrete type; declare the type explicitly when determining it requires reading a called method declaration or relying on generic type inference. Test source sets include unit tests, integration tests, and dedicated test support; a type in production sources is not exempt merely because its name contains `Test`.
- **JAVA-RECORD-001** **[DEFAULT][BASELINE]** — Prefer `record` for data carriers whose state is complete at construction, remains immutable, and does not require inheritance, proxies, or JavaBean setter binding.
- **JAVA-RECORD-002** **[REQUIRED][BASELINE]** — Defensively copy collection record components with `List.copyOf`, `Set.copyOf`, or the corresponding immutable-copy operation.
- **JAVA-CLASS-001** **[REQUIRED][BASELINE]** — Use an ordinary class when the type requires inheritance, mutable state, framework proxying, JavaBean shape, or complex domain behavior.
- **JAVA-TYPE-001** **[DEFAULT][BASELINE]** — Prefer wrapper types such as `Integer`, `Long`, and `Boolean`; use primitives only for intentional default values, performance characteristics, or explicitly non-null semantics.

## Readable modern Java

- **JAVA-BRACES-001** **[REQUIRED][BASELINE]** — In both production and test code, always use braces for the bodies of `if`, `else`, `for` (including enhanced for), `while`, and `do-while`, even for a single statement or an empty body; never use a lone semicolon as an empty body. An `else if` chain is allowed, but every conditional branch body must still use braces.
- **JAVA-STYLE-001** **[ADVISORY][BASELINE]** — For simple collection traversal, `forEach` is an available concise style; choose it only when it remains immediately readable.
- **JAVA-STYLE-002** **[ADVISORY][BASELINE]** — For simple transformations and callbacks, Stream, lambdas, and method references are available concise styles; choose them only when they remain immediately readable.
- **JAVA-OPTIONAL-001** **[DEFAULT][BASELINE]** — Prefer `Optional` for possibly absent return values and chained handling.
- **JAVA-OPTIONAL-002** **[REQUIRED][BASELINE]** — Do not use `Optional` as a Bean property or method parameter.
- **JAVA-READABILITY-001** **[REQUIRED][BASELINE]** — For complex business rules, nested conditions, and exception flows, prioritize readability over functional style.
