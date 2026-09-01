# Constants and Literal Rules

## Business values

- **JAVA-LITERAL-001** — Do not hard-code a string literal that carries business meaning, is reused, or participates in branching or state identification; a default value declared in configuration is exempt.
- **JAVA-LITERAL-002** — Represent a fixed closed value set with an enum, a reusable single value with a `public static final` constant or dedicated constants type, and a deployment-varying value with external configuration, which may declare a default.
- **JAVA-LITERAL-EXCEPTION-001** — A log message, non-business diagnostic exception message, test fixture value, regular expression, or format template may remain a literal only when it carries no business meaning, does not drive branching or state, and needs no cross-class reuse.
