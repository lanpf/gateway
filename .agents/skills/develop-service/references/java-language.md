# Java Language Rules

## Contents

- [Language and data design](#language-and-data-design)
- [Collection ownership and defensive copying](#collection-ownership-and-defensive-copying)
- [Readable modern Java](#readable-modern-java)

## Language and data design

- **JAVA-VERSION-002** **[REQUIRED][BASELINE]** — Compile and run with Java 17.
- **JAVA-IMPORT-UNUSED-001** **[REQUIRED][BASELINE]** — Production and test code must not retain unused type imports or static imports. Check and remove unused imports after creating, changing, or refactoring a Java file; possible future use is not a reason to retain them.
- **JAVA-VAR-001** **[REQUIRED][BASELINE]** — Production code must not use `var`, including local variables, loop variables, try-with-resources variables, and lambda parameters; use an explicit type wherever a type declaration is needed.
- **JAVA-VAR-002** **[REQUIRED][BASELINE]** — Only test source sets may use `var`, and only when the initializer itself clearly shows the concrete type; declare the type explicitly when determining it requires reading a called method declaration or relying on generic type inference. Test source sets include unit tests, integration tests, and dedicated test support; a type in production sources is not exempt merely because its name contains `Test`.
- **JAVA-RECORD-001** **[DEFAULT][BASELINE]** — Prefer `record` for data carriers whose state is complete at construction, remains immutable, and does not require inheritance, proxies, or JavaBean setter binding.
- **JAVA-CLASS-001** **[REQUIRED][BASELINE]** — Use an ordinary class when the type requires inheritance, mutable state, framework proxying, JavaBean shape, or complex domain behavior.
- **JAVA-TYPE-001** **[DEFAULT][BASELINE]** — Prefer wrapper types such as `Integer`, `Long`, and `Boolean`; use primitives only for intentional default values, performance characteristics, or explicitly non-null semantics.

## Collection ownership and defensive copying

- **JAVA-COLLECTION-OWNERSHIP-001** **[REQUIRED][BASELINE]** — When an object retains an externally mutable collection or Map as its state or for long-term use, defensively copy it at the actual retention boundary, such as its constructor, factory, or state-update method. This applies to records and ordinary classes; do not rely on upstream copying. Temporary reads, traversal, or forwarding do not require copies merely because a collection is passed. Reuse inputs only when an explicit immutable contract guarantees that other aliases cannot change the container contents. An unmodifiable view alone does not isolate changes to the original mutable container.
- **JAVA-COLLECTION-COPY-MUTABILITY-001** **[REQUIRED][BASELINE]** — Choose retained-copy mutability according to the object's state contract. Records and other immutable data carriers retain immutable copies through List.copyOf, Set.copyOf, Map.copyOf, or an equivalent operation; objects owning mutable collection state retain independent mutable copies. Defensive copying does not universally mean freezing. Validation rules own null-container, empty-container, null-element semantics and concrete tool selection.
- **JAVA-COLLECTION-ELEMENT-ISOLATION-001** **[REQUIRED][BASELINE]** — Copying a container does not isolate its elements. When the contract requires isolation of mutable elements, nested containers, or Map keys or values, copy the corresponding contents or convert them to explicitly immutable values. An unmodifiable outer container does not establish whole-object immutability; do not mechanically deep-copy already immutable elements.

## Readable modern Java

- **JAVA-BRACES-001** **[REQUIRED][BASELINE]** — In both production and test code, always use braces for the bodies of `if`, `else`, `for` (including enhanced for), `while`, and `do-while`, even for a single statement or an empty body; never use a lone semicolon as an empty body. An `else if` chain is allowed, but every conditional branch body must still use braces.
- **JAVA-STYLE-001** **[ADVISORY][BASELINE]** — For simple collection traversal, `forEach` is an available concise style; choose it only when it remains immediately readable.
- **JAVA-STYLE-002** **[ADVISORY][BASELINE]** — For simple transformations and callbacks, Stream, lambdas, and method references are available concise styles; choose them only when they remain immediately readable.
- **JAVA-OPTIONAL-001** **[DEFAULT][BASELINE]** — Prefer `Optional` for possibly absent return values and chained handling.
- **JAVA-OPTIONAL-002** **[REQUIRED][BASELINE]** — Do not use `Optional` as a Bean property or method parameter.
- **JAVA-READABILITY-001** **[REQUIRED][BASELINE]** — For complex business rules, nested conditions, and exception flows, prioritize readability over functional style.
