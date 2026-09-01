# Service Architecture Rules

## Contents

- [Service domain contract](#service-domain-contract)
- [Modules](#modules)
- [Dependency direction](#dependency-direction)
- [Project configuration](#project-configuration)

## Service domain contract

- **SERVICE-DOC-ENTRY-001** — Every service declares a project documentation entry that summarizes the project and routes tasks to authoritative documents.
- **SERVICE-DOMAIN-DOC-003** — Every service keeps an authoritative domain document defining its bounded context, domain language, business rules, error definitions, domain events, and API semantics; its path is declared by the project documentation entry and project guidance.
- **SERVICE-DOMAIN-DOC-002** — Project domain guidance may add or tighten shared rules but must not duplicate, relax, or override them.

## Modules

- **ARCH-MODULE-API-001** — `<service>-api` defines stable external API contracts.
- **ARCH-MODULE-DOMAIN-001** — `<service>-domain` owns the core domain model and rules.
- **ARCH-MODULE-APPLICATION-001** — `<service>-application` orchestrates write use cases and read-only queries.
- **ARCH-MODULE-INFRA-001** — `<service>-infrastructure` contains technical adapters, persistence abstractions, and technology-neutral shared conversions, but no DOs or conversion mappers tied to a concrete persistence technology.
- **ARCH-MODULE-TECH-001** — Concrete persistence, scheduler, and message implementations live in `<service>-infrastructure-persistence-<technology>`, `<service>-infrastructure-scheduler-<technology>`, and `<service>-infrastructure-message-<technology>` modules.
- **ARCH-MODULE-TECH-002** — A persistence implementation module also hosts that technology stack's DOs, DO conversion mappers, persistence repositories, SQL/XML, and assembly.
- **ARCH-MODULE-INTERFACES-001** — `<service>-interfaces` implements protocol-neutral Facades and hosts REST, RPC, and message subscription adapters.
- **ARCH-MODULE-CLIENT-001** — `<service>-openfeign-client` provides the OpenFeign client for the service API.
- **ARCH-MODULE-BOOT-001** — `<service>-boot` starts, configures, and packages the service.
- **ARCH-MODULE-INTEGRATION-TESTS-001** — `<service>-integration-tests` is a standalone test module for cross-module, full-auto-configuration, and real database or middleware integration tests; production modules must not depend on it.

## Dependency direction

- **ARCH-DEP-API-001** — API does not depend on business implementations; domain does not depend on other business layers; application depends on domain.
- **ARCH-DEP-INFRA-001** — Infrastructure implements domain and application ports; concrete persistence, scheduler, and message modules depend on infrastructure.
- **ARCH-DEP-OUTER-001** — Interfaces depends on API and application, OpenFeign client depends on API, and boot performs final assembly.
- **ARCH-DEP-INTEGRATION-TESTS-001** — Integration-tests may depend on boot and observed service modules in test scope only; those verification dependencies never participate in production dependency direction.
- **ARCH-DEP-INWARD-001** — No inner module may depend on outer protocol types, persistence objects, concrete technologies, or boot.

## Project configuration

- **ARCH-PROJECT-CONFIG-001** — Create a root-level `config/` directory beside service modules as the preferred home for independently updateable resources.
- **ARCH-PROJECT-CONFIG-003** — Project-level `application.yml`, `application-*.yml` or corresponding properties, MyBatis shared SQL fragments and mapper XML, database schema or migration scripts, Dubbo XML, and similar configuration are stored there and are not packaged into a module artifact merely because that module assembles them.
- **ARCH-PROJECT-CONFIG-002** — Load root-level `config/` resources explicitly through Spring or technology-specific location settings and make build, deployment, and local startup provide and validate them; package a resource under a module only when external loading is unsupported, code and resource versions are inseparable, or the artifact must be self-contained.
