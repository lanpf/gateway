# Service Framework and Assembly Rules

## Framework baseline

- **SERVICE-SPRING-VERSION-PAIR-001** **[REQUIRED][BASELINE]** — Use the unified service version pair: Spring Boot `3.5.16` with Spring Cloud `2025.0.3`.
- **SERVICE-SPRING-VERSION-UPGRADE-001** **[REQUIRED][BASELINE]** — Upgrade the version pair with `framework-dependencies` and update the policy table in the same release.
- **SERVICE-BOM-001** **[REQUIRED][BASELINE]** — Use `framework-dependencies` and `framework-starter-dependencies` for centralized versions, selecting `framework-*` contracts and `framework-starter-*` implementations as needed.
- **SERVICE-NEUTRAL-001** **[REQUIRED][BASELINE]** — Technology-neutral domain and application modules depend only on the minimum framework contracts required by their responsibility.

## Runtime assembly

- **SERVICE-ASSEMBLY-001** **[REQUIRED][BASELINE]** — Select replaceable persistence, messaging, and scheduling implementations through boot packaging dependencies.
- **SERVICE-ASSEMBLY-002** **[REQUIRED][BASELINE]** — Do not switch multiple technology implementations inside one artifact using runtime properties.
