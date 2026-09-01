# Service Framework and Assembly Rules

## Framework baseline

- **SERVICE-SPRING-BOOT-001** — Service applications use Spring Boot 3.5.16.
- **SERVICE-SPRING-CLOUD-001** — Service applications use Spring Cloud 2025.0.3.
- **SERVICE-BOM-001** — Use `framework-dependencies` and `framework-starter-dependencies` for centralized versions, selecting `framework-*` contracts and `framework-starter-*` implementations as needed.
- **SERVICE-NEUTRAL-001** — Technology-neutral domain and application modules depend only on the minimum framework contracts required by their responsibility.

## Runtime assembly

- **SERVICE-ASSEMBLY-001** — Select replaceable persistence, messaging, and scheduling implementations through boot packaging dependencies.
- **SERVICE-ASSEMBLY-002** — Do not switch multiple technology implementations inside one artifact using runtime properties.
