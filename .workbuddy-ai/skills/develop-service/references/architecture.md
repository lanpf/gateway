# Service Architecture Rules

## Contents

- [Service domain contract](#service-domain-contract)
- [Modules](#modules)
- [Package structure](#package-structure)
- [Dependency direction](#dependency-direction)
- [Responsibility cohesion and boundary checks](#responsibility-cohesion-and-boundary-checks)
- [Project configuration](#project-configuration)

## Service domain contract

- **SERVICE-DOMAIN-DOC-002** **[REQUIRED][TOPIC]** — Read authoritative project documents before changing layering, domain boundaries, or API business semantics; project rules may add or tighten shared rules but must not duplicate, relax, or override them.

## Modules

- **ARCH-MODULE-API-001** **[REQUIRED][TOPIC]** — `<service>-api` defines stable external API contracts.
- **ARCH-MODULE-DOMAIN-001** **[REQUIRED][TOPIC]** — `<service>-domain` owns the core domain model and rules.
- **ARCH-MODULE-APPLICATION-001** **[REQUIRED][TOPIC]** — `<service>-application` orchestrates write use cases and read-only queries.
- **ARCH-MODULE-INFRA-001** **[REQUIRED][TOPIC]** — `<service>-infrastructure` contains technical adapters, persistence abstractions, and technology-neutral shared conversions, but no DOs or conversion mappers tied to a concrete persistence technology.
- **ARCH-MODULE-TECH-001** **[REQUIRED][TOPIC]** — Concrete persistence, scheduler, and message implementations live in `<service>-infrastructure-persistence-<technology>`, `<service>-infrastructure-scheduler-<technology>`, and `<service>-infrastructure-message-<technology>` modules.
- **ARCH-MODULE-TECH-002** **[REQUIRED][TOPIC]** — A persistence implementation module also hosts that technology stack's DOs, DO conversion mappers, persistence repositories, SQL/XML, and assembly.
- **ARCH-MODULE-INTERFACES-001** **[REQUIRED][TOPIC]** — `<service>-interfaces` implements protocol-neutral Facades and hosts REST, RPC, and message subscription adapters.
- **ARCH-MODULE-CLIENT-001** **[REQUIRED][TOPIC]** — `<service>-openfeign-client` provides the OpenFeign client for the service API.
- **ARCH-MODULE-BOOT-001** **[REQUIRED][TOPIC]** — `<service>-boot` starts, configures, and packages the service.
- **ARCH-MODULE-INTEGRATION-TESTS-001** **[REQUIRED][TOPIC]** — `<service>-integration-tests` is a standalone test module for cross-module, full-auto-configuration, and real database or middleware integration tests; production modules must not depend on it.

## Package structure

- **PACKAGE-LAYER-001** **[REQUIRED][TOPIC]** — Top-level packages must match the module responsibility, such as `api`, `domain`, `application`, `infrastructure`, or `interfaces`; do not place production types from different layers or technology adapters directly in the module root package.
- **PACKAGE-DIRECTION-001** **[REQUIRED][TOPIC]** — Package dependencies must follow the service layer dependency direction; package boundaries must not bypass module constraints or hide reverse dependencies through cross-package access, reflection, or events.
- **PACKAGE-FEATURE-001** **[DEFAULT][TOPIC]** — Within a layer, group packages by business capability or technical adapter before role subpackages; do not create global `entity`, `service`, or `repository` buckets by default.
- **PACKAGE-INFRA-001** **[REQUIRED][TOPIC]** — The `infrastructure` module must further group packages by capability or adapter boundary, such as `persistence`, `messaging`, `locking`, `external`, or `configuration`; do not place all adapters, DOs, mappers, configuration, and clients in one `infra` package.
- **PACKAGE-TECH-001** **[REQUIRED][TOPIC]** — A concrete technology module groups packages first by technology stack and then by aggregate, resource, or external service; technology-specific DOs, repositories, conversion mappers, and assembly may share a feature package or its role subpackages, but concrete DOs and technology annotations must not be shared across stacks.
- **PACKAGE-CROSS-CAPABILITY-001** **[REQUIRED][TOPIC]** — Cross-capability reuse must use a stable port, contract, or explicit shared component; do not couple capabilities through another feature package's implementation classes, database DOs, or mappers.
- **PACKAGE-GRANULARITY-001** **[DEFAULT][TOPIC]** — Create a subpackage only when its types form a clear responsibility boundary; do not create a package for a single type merely for visual symmetry, and split oversized packages by capability or reason for change rather than mechanically by class name.
- **PACKAGE-VISIBILITY-001** **[REQUIRED][TOPIC]** — Keep implementation types package-private by default; declare a type `public` only when cross-package, cross-module, or framework assembly requires it, and give every public type an explicit contract or adapter responsibility.
- **PACKAGE-TEST-001** **[DEFAULT][TOPIC]** — Mirror production package paths in tests; keep test helpers in test source sets or test-support packages and never package them as production code or make production code depend on them.

## Dependency direction

- **ARCH-DEP-API-001** **[REQUIRED][TOPIC]** — API does not depend on business implementations; domain does not depend on other business layers; application depends on domain.
- **ARCH-DEP-INFRA-001** **[REQUIRED][TOPIC]** — Infrastructure implements domain and application ports; concrete persistence, scheduler, and message modules depend on infrastructure.
- **ARCH-DEP-OUTER-001** **[REQUIRED][TOPIC]** — Interfaces depends on API and application, OpenFeign client depends on API, and boot performs final assembly.
- **ARCH-DEP-INTEGRATION-TESTS-001** **[REQUIRED][TOPIC]** — Integration-tests may depend on boot and observed service modules in test scope only; those verification dependencies never participate in production dependency direction.
- **ARCH-DEP-INWARD-001** **[REQUIRED][TOPIC]** — No inner module may depend on outer protocol types, persistence objects, concrete technologies, or boot.

## Responsibility cohesion and boundary checks

- **ARCH-SEMANTIC-OWNERSHIP-001** **[REQUIRED][TOPIC]** — Determine ownership from authoritative project responsibility/domain documents and business semantics, not names, packages, annotations, or branch counts. Protocol parsing, serialization, connection management, and technical retries that preserve business semantics are adapter behavior. Classify gateway protocol authentication and route execution by their actual semantics rather than automatically moving them into domain/application.
- **ARCH-DOMAIN-COHESION-001** **[REQUIRED][TOPIC]** — Locate where eligibility decisions, business invariants, state transitions, and business decisions actually execute. Keep each rule in its owning domain model or domain service and reuse it across entry points; controllers, filters, listeners, adapters, and application must not maintain independent copies of domain decisions. Distinguish protocol-format validation from domain validity using the validation rules.
- **ARCH-ORCHESTRATION-COHESION-001** **[REQUIRED][TOPIC]** — Trace use-case call chains to locate business step ordering, coordination of domain capabilities or ports, business-result branching, and business compensation decisions; application owns this orchestration. Multiple SDK calls, page fetching, or technical retries within one adapter operation do not alone imply application orchestration; independent infrastructure recovery follows the compensation rules.
- **ARCH-SUBSTANTIVE-LAYERING-001** **[REQUIRED][TOPIC]** — Do not treat the existence of domain/application modules or an intermediate forwarding service as proof of cohesion; trace the implementation that actually decides and orchestrates. Responsibility migration must move the decisions and their behavioral verification, not merely move classes, rename packages, or add forwarding layers while leaving business logic in outer layers.
- **ARCH-DEPENDENCY-AUDIT-001** **[REQUIRED][TOPIC]** — Check dependency direction in declared/inherited POM dependencies and source imports, fully qualified type references, method signatures, inheritance/implementation relationships, and injected types; trace related call chains for implicit reverse dependencies. Apply the allowed layer dependencies and type boundaries, not merely successful compilation, absence of cycles, or use of interfaces.

## Project configuration

- **ARCH-PROJECT-CONFIG-001** **[REQUIRED][TOPIC]** — Create a root-level `config/` directory beside service modules as the preferred home for independently updateable resources.
- **ARCH-PROJECT-CONFIG-003** **[DEFAULT][TOPIC]** — Prefer the project-root `config/` directory for project-level application configuration, MyBatis SQL/XML, database schema or migrations, Dubbo XML, and other independently updateable resources.
- **ARCH-PROJECT-CONFIG-PACKAGING-001** **[REQUIRED][TOPIC]** — Do not package an independently updateable configuration resource in a module artifact merely because that module assembles the capability.
- **ARCH-PROJECT-CONFIG-002** **[REQUIRED][TOPIC]** — Load root-level `config/` resources explicitly through Spring or technology-specific location settings and make build, deployment, and local startup provide and validate them; package a resource under a module only when external loading is unsupported, code and resource versions are inseparable, or the artifact must be self-contained.
- **CONFIG-DEFAULT-001** **[REQUIRED][TOPIC]** — When a `*Properties` type already provides a default value and the configuration file does not change it, the configuration file must not restate the property; configuration files carry only explicit decisions that differ from code defaults and externally varying values.
- **CONFIG-PLACEHOLDER-001** **[REQUIRED][TOPIC]** — Configuration properties must not introduce environment-variable placeholders by default and must not require the deployment environment to provide a variable by default; when injection is genuinely needed, the deployment configuration or a dedicated profile carries it, and a missing value must fail fast.
