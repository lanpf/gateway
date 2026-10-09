# Project Documentation Rules

## Contents

- [Required documents](#required-documents)
- [Document boundaries](#document-boundaries)
- [Cross-service contracts](#cross-service-contracts)

## Required documents

- **SERVICE-DOC-ENTRY-001** **[REQUIRED][TOPIC]** — Every service declares a project documentation entry that routes each task to the required authoritative documents.
- **SERVICE-DOMAIN-DOC-003** **[REQUIRED][TOPIC]** — Every business service keeps `docs/DOMAIN.md` as the authoritative bounded-context document for domain language, rules, errors, events, and API business semantics.
- **DOCS-BASE-FILES-001** **[REQUIRED][TOPIC]** — Every project provides root `README.md` and `docs/RESPONSIBILITIES.md`.
- **DOCS-INFRA-DOMAIN-001** **[DEFAULT][TOPIC]** — An infrastructure project that owns a real domain should provide `docs/DOMAIN.md`; a pure library does not need one.
- **DOCS-AUTH-DOC-001** **[DEFAULT][TOPIC]** — The documents required for the project type are a floor, not the full set; key infrastructure capabilities or external dependencies may have separate authoritative documents routed by the project documentation entry, with short names.

## Document boundaries

- **DOCS-README-001** **[REQUIRED][TOPIC]** — `README.md` is the project map answering what the project is, how to build, run, and verify it, where modules live, and where to read more; it must not carry responsibility philosophy or domain rules, and capability delivery status stays as a one-line overview linking to `docs/RESPONSIBILITIES.md`.
- **DOCS-RESPONSIBILITY-SCOPE-001** **[REQUIRED][TOPIC]** — `docs/RESPONSIBILITIES.md` defines owned/unowned responsibilities, collaboration contracts, and delivery extent; it is the single home for cross-service collaboration contracts.
- **DOCS-UNOWNED-OWNER-001** **[REQUIRED][TOPIC]** — Each unowned responsibility listed in the responsibility document must identify its owner.
- **DOCS-DOMAIN-001** **[REQUIRED][TOPIC]** — `docs/DOMAIN.md` is the domain design answering how the domain is modeled, carrying ubiquitous language, models, invariants, use cases, domain events and errors, and layering; it must not carry deployment or runtime configuration, delivery status, or other services' responsibilities.

## Cross-service contracts

- **DOCS-CONTRACT-SPLIT-001** **[REQUIRED][TOPIC]** — Each side writes its half of a cross-service contract in its own `docs/RESPONSIBILITIES.md` and links to the other: the caller owns end-to-end flow and the callee owns endpoint contract and consumption semantics. Never duplicate the same contract table or flow.
- **DOCS-CONTRACT-UPDATE-001** **[REQUIRED][TOPIC]** — When a cross-service contract changes, both sides update their owned content together.
- **DOCS-CONTRACT-LINK-001** **[REQUIRED][TOPIC]** — Reference the other side's authoritative document by link instead of copying its content.
