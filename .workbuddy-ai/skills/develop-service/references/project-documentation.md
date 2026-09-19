# Project Documentation Rules

## Contents

- [Required documents](#required-documents)
- [Document boundaries](#document-boundaries)
- [Cross-service contracts](#cross-service-contracts)

## Required documents

- **SERVICE-DOC-ENTRY-001** **[REQUIRED][TOPIC]** — Every service declares a project documentation entry that routes each task to the required authoritative documents.
- **SERVICE-DOMAIN-DOC-003** **[REQUIRED][TOPIC]** — Every business service keeps `docs/DOMAIN.md` as the authoritative bounded-context document for domain language, rules, errors, events, and API business semantics.
- **DOCS-STRUCT-001** **[REQUIRED][TOPIC]** — Every project provides a root `README.md` and `docs/RESPONSIBILITIES.md`; every business service also provides `docs/DOMAIN.md`.
- **DOCS-INFRA-DOMAIN-001** **[DEFAULT][TOPIC]** — An infrastructure project that owns a real domain should provide `docs/DOMAIN.md`; a pure library does not need one.
- **DOCS-AUTH-DOC-001** **[ADVISORY][TOPIC]** — The required documents are a floor, not the full set; key infrastructure capabilities or external dependencies may have separate authoritative documents routed by the project documentation entry, and document names should stay short.

## Document boundaries

- **DOCS-README-001** **[REQUIRED][TOPIC]** — `README.md` is the project map answering what the project is, how to build, run, and verify it, where modules live, and where to read more; it must not carry responsibility philosophy or domain rules, and capability delivery status stays as a one-line overview linking to `docs/RESPONSIBILITIES.md`.
- **DOCS-RESPONSIBILITIES-001** **[REQUIRED][TOPIC]** — `docs/RESPONSIBILITIES.md` is the service charter answering what the service owns, what it does not own (each entry naming its owner), which collaboration contracts it honors, and what has been delivered; it is the single home for cross-service collaboration contracts.
- **DOCS-DOMAIN-001** **[REQUIRED][TOPIC]** — `docs/DOMAIN.md` is the domain design answering how the domain is modeled, carrying ubiquitous language, models, invariants, use cases, domain events and errors, and layering; it must not carry deployment or runtime configuration, delivery status, or other services' responsibilities.

## Cross-service contracts

- **DOCS-CROSS-SERVICE-001** **[REQUIRED][TOPIC]** — Each side of a cross-service contract writes only its own half in its own `docs/RESPONSIBILITIES.md` and links to the other side: the caller owns the end-to-end flow description, the callee owns the endpoint contract and its consumption semantics; never maintain the same contract table or flow in two repositories.
