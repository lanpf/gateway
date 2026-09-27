# Constants and Literal Rules

## Business values

- **JAVA-LITERAL-001** **[REQUIRED][BASELINE]** — Do not hard-code string literals carrying business meaning, requiring existing cross-class reuse, or identifying business branches or states; configuration defaults are exempt. Stable protocol keys follow their adapter rules. Purely local technical separators with no business/protocol convention or cross-class reuse may remain literals; hypothetical future reuse alone does not require extraction.
- **JAVA-LITERAL-002** **[REQUIRED][BASELINE]** — Represent a fixed closed value set with an enum, a reusable single value with a `public static final` constant or dedicated constants type, and a deployment-varying value with external configuration, which may declare a default.
- **JAVA-LITERAL-EXCEPTION-001** **[REQUIRED][BASELINE]** — A log message, non-business diagnostic exception message, test fixture value, regular expression, or format template may remain a literal only when it carries no business meaning, does not drive branching or state, and needs no cross-class reuse.
