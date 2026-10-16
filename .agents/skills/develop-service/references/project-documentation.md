# Project Documentation Rules

## Contents

- [Required documents](#required-documents)
- [Document boundaries](#document-boundaries)
- [Domain writing](#domain-writing)
- [Technical design ownership](#technical-design-ownership)
- [Authority and delivery status](#authority-and-delivery-status)
- [Cross-service contracts](#cross-service-contracts)

## Required documents

- **SERVICE-DOC-ENTRY-001** **[REQUIRED][TOPIC]** — Every service declares a project documentation entry that routes each task to the required authoritative documents.
- **SERVICE-DOMAIN-DOC-003** **[REQUIRED][TOPIC]** — Every business service keeps `docs/DOMAIN.md` as the authoritative bounded-context document for domain language, rules, errors, events, and API business semantics.
- **DOCS-BASE-FILES-001** **[REQUIRED][TOPIC]** — Every project provides root `README.md` and `docs/RESPONSIBILITIES.md`.
- **DOCS-INFRA-DOMAIN-001** **[DEFAULT][TOPIC]** — An infrastructure project that owns a real domain should provide `docs/DOMAIN.md`; a pure library does not need one.
- **DOCS-AUTH-DOC-001** **[DEFAULT][TOPIC]** — The documents required for the project type are a floor, not the full set; key infrastructure capabilities or external dependencies may have separate authoritative documents routed by the project documentation entry, with short names.

## Document boundaries

- **DOCS-README-001** **[REQUIRED][TOPIC]** — `README.md` is the project map answering what the project is, how to build, run, and verify it, where modules live, and where to read more; it must not carry responsibility philosophy or domain rules, and capability delivery status stays as a one-line overview linking to `docs/RESPONSIBILITIES.md`.
- **DOCS-RESPONSIBILITY-SCOPE-001** **[REQUIRED][TOPIC]** — `docs/RESPONSIBILITIES.md` defines service boundaries and external commitments: owned/unowned responsibilities, collaboration contracts, guarantees, limitations, and delivery extent. It is the single home for cross-service collaboration contracts. External audiences include services, clients, operations callers, and message consumers, not internal classes or modules.
- **DOCS-UNOWNED-OWNER-001** **[REQUIRED][TOPIC]** — Each unowned responsibility listed in the responsibility document must identify its owner.
- **DOCS-DOMAIN-001** **[REQUIRED][TOPIC]** — `docs/DOMAIN.md` defines the bounded context, ubiquitous language, model identity and relationships, aggregate boundaries, states, invariants, decision order, transitions, use-case intent, preconditions and success/failure outcomes, domain events, and business errors. It must not carry deployment or runtime configuration, delivery status, or other services' responsibilities.
- **DOCS-IMPLEMENTATION-EXCLUSION-001** **[REQUIRED][TOPIC]** — Neither `DOMAIN.md` nor `RESPONSIBILITIES.md` is an implementation manual: do not specify language or class/record choices, annotations, base classes, getters/setters, generics or collection implementations, construction mechanisms, implementation method signatures or class call chains, framework usage, assembly, or cache optimizations. Interface definitions necessary for a published protocol remain in their authoritative contract and are not internal implementation details.

## Domain writing

- **DOCS-DOMAIN-LANGUAGE-001** **[REQUIRED][TOPIC]** — Express domain semantics in ubiquitous language; business-aligned model names are allowed, but class definitions, implementation signatures, and code call chains must not replace rules. Retain intrinsic concepts, business meaning, security constraints, and failure semantics of technical domains such as authentication, signatures, headers, and quotas.
- **DOCS-DOMAIN-LOGICAL-BOUNDARY-001** **[REQUIRED][TOPIC]** — Domain responsibility boundaries describe ownership of business decisions and the semantic roles of use cases and models, not concrete modules, packages, classes, technical adapters, or component dependencies. Follow the layered-service BP for code layering and dependency conventions.
- **DOCS-DOMAIN-PROTOCOL-SEMANTICS-001** **[REQUIRED][TOPIC]** — Keep protocol-related business meaning, security constraints, and failure semantics in the domain document; link to the authoritative protocol contract for message structure, encoding, HTTP mappings, and interoperability. Distinguish domain rejection decisions from externally visible status codes and retry commitments.
- **DOCS-DOMAIN-CURRENT-001** **[REQUIRED][TOPIC]** — Domain prose defines the currently accepted domain design; do not organize rules as this-round changes, current implementation notes, or before/after migration narratives. Implementation progress and historical verification results are not domain-rule text.
- **DOCS-DOMAIN-PORTABILITY-001** **[DEFAULT][TOPIC]** — When reviewing domain prose, hold business semantics and external contracts constant and ask whether it survives a language, framework, or storage replacement; move implementation-dependent descriptions out by default. Do not use this check to remove intrinsic technical-domain concepts or protocol semantics.
- **DOCS-DOMAIN-OUTLINE-001** **[ADVISORY][TOPIC]** — Consider organizing DOMAIN as ubiquitous language, models and relationships, invariants, decisions and transitions, use cases, then business errors and domain events; adapt to the real domain and do not create empty sections mechanically.

## Technical design ownership

- **DOCS-TECHNICAL-DESIGN-001** **[REQUIRED][TOPIC]** — Maintain project-specific architecture or technical decisions worth preserving in a separate technical design document routed by README, not by dumping implementation prose into README. Do not require a new document when no lasting project-specific decision needs one.
- **DOCS-SHARED-RULE-REFERENCE-001** **[REQUIRED][TOPIC]** — Reference the owning BP for shared implementation conventions rather than repeating them in project documents; maintain only genuine project-specific additions, not copied shared rules.

## Authority and delivery status

- **DOCS-AUTHORITY-001** **[REQUIRED][TOPIC]** — Maintain each rule, contract table, or flow in one authoritative source. Other documents may summarize and link, but must not copy complete decision tables, flows, or constraints; referring to a shared concept is allowed, duplicate definitions and maintenance are not.
- **DOCS-DESIGN-DELIVERY-001** **[REQUIRED][TOPIC]** — Separate accepted domain semantics in DOMAIN from implementation, verified extent, and unmet limitations in RESPONSIBILITIES. Missing implementation must not redefine the domain rule, and a valid design must not be claimed as delivered.
- **DOCS-DELIVERY-CURRENT-001** **[REQUIRED][TOPIC]** — Keep responsibility delivery sections focused on current capability status, limitations, and necessary verification evidence, not an accumulating development diary. Keep rename histories, constructor changes, per-round test counts, and detailed migrations in commit, release, or verification records and link when needed; historical tests do not prove current-worktree acceptance.
- **DOCS-RESPONSIBILITY-OUTLINE-001** **[ADVISORY][TOPIC]** — Consider organizing RESPONSIBILITIES as responsibility boundaries, collaboration contracts, external guarantees and limitations, then current delivery status, emphasizing data ownership, trust boundaries, and failure responsibility rather than internal class or component lists.

## Cross-service contracts

- **DOCS-CONTRACT-SPLIT-001** **[REQUIRED][TOPIC]** — Each side writes its half of a cross-service contract in its own RESPONSIBILITIES and links to the other: the caller owns the cross-service collaboration flow, and the callee owns its endpoint contract and consumption semantics. The flow describes services, collaboration order, exchanged data, and failure responsibility, not internal class call chains or the domain use case's decision rules, which belong to DOMAIN. Never duplicate the same contract table or flow.
- **DOCS-CONTRACT-UPDATE-001** **[REQUIRED][TOPIC]** — When a cross-service contract changes, both sides update their owned content together.
- **DOCS-CONTRACT-LINK-001** **[REQUIRED][TOPIC]** — Reference the other side's authoritative document by link instead of copying its content.
