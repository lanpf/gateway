# Service Error Code Rules

## Contents

- [Code structure](#code-structure)
- [Domain allocation](#domain-allocation)
- [Stability](#stability)

## Code structure

- **ERROR-FORMAT-001** **[REQUIRED][TOPIC]** — A complete error code has six digits: a three-digit service prefix plus a three-digit local code; error enums declare only the local code, and the framework composes and zero-pads the full code.
- **ERROR-CONTEXT-001** **[REQUIRED][TOPIC]** — One service project represents one bounded context and uses one unique service prefix; independently deployed bounded contexts require distinct prefixes.
- **ERROR-PREFIX-REGISTRY-001** **[REQUIRED][TOPIC]** — A service prefix must be centrally registered and allocated within the deployment system before use; two services in the same deployment system must never use the same prefix.
- **ERROR-DOMAIN-001** **[REQUIRED][TOPIC]** — Allocate local codes `000-599` to `DomainError`: reserve `000-099` for shared domain errors and partition `100-599` into aligned 50-code blocks. Each aggregate is assigned one or more adjacent complete blocks according to its required capacity.
- **ERROR-DOMAIN-CAPACITY-001** **[REQUIRED][TOPIC]** — Aggregate code blocks align to 50-code boundaries (100–149, 150–199, …, 550–599) and 50 codes is the minimum allocation unit. An aggregate exclusively owns every code in all of its assigned blocks and may use one or multiple adjacent blocks. Capacity expansion must reserve additional adjacent, currently unallocated complete blocks; blocks must not be split, shared, or reassigned between aggregates.
- **ERROR-APPLICATION-001** **[REQUIRED][TOPIC]** — Allocate local codes `600-699` to `ApplicationError` and prefix names with `APP_`.
- **ERROR-INFRA-001** **[REQUIRED][TOPIC]** — Allocate local codes `700-899` to `InfrastructureError`.
- **ERROR-INFRA-TECH-001** **[REQUIRED][TOPIC]** — Reserve `700-799` for failures of technical mechanisms owned by the service, such as local persistence, caching, locking, serialization, files, event storage, and outbox processing; prefix names with `INFRA_TECH_`.
- **ERROR-INFRA-ACL-001** **[REQUIRED][TOPIC]** — Use local codes 800–899 and the `INFRA_ACL_` prefix for failures at an external-integration adapter boundary; ACL names the anti-corruption responsibility and does not require a separately named module.
- **ERROR-INFRA-TRANSLATION-001** **[REQUIRED][TOPIC]** — Translate an external rejection into `DomainError` or `ApplicationError` when it has a stable local business meaning; do not classify it as infrastructure solely because it originated from another system.
- **ERROR-RESERVED-001** **[REQUIRED][TOPIC]** — Keep local codes `900-999` reserved and unallocated.

## Domain allocation

- **ERROR-DOMAIN-COMMON-001** **[REQUIRED][TOPIC]** — Prefix shared domain errors with `DOMAIN_`; fix local code `000` as `DOMAIN_ENTITY_ID_INVALID`.
- **ERROR-AGGREGATE-001** **[REQUIRED][TOPIC]** — Prefix aggregate errors with the aggregate name; the first two are `{AGGREGATE}_NOT_FOUND` and `{AGGREGATE}_ALREADY_EXISTS`.
- **ERROR-ENUM-PLACEMENT-001** **[REQUIRED][TOPIC]** — Error enums live in the module owning their semantics: domain errors in domain, application errors in application, and infrastructure errors in the corresponding infrastructure module. An adapter translating and throwing a domain/application exception does not change that ownership.
- **ERROR-ENUM-NAMING-001** **[REQUIRED][TOPIC]** — Organize and name error enums by owning aggregate, technical mechanism, or the shared-domain segment; do not create one enum mixing code segments from different layers.
- **ERROR-UNIQUENESS-001** **[REQUIRED][TOPIC]** — A complete error code is globally unique within one deployment system; a local code need only be unique within its service prefix.
- **ERROR-RANGE-001** **[REQUIRED][TOPIC]** — Unallocated complete aggregate ranges remain available, but unused codes inside an assigned aggregate range remain owned by that aggregate and must not be reassigned.
- **ERROR-DOCUMENTATION-002** **[REQUIRED][TOPIC]** — Document the service prefix, shared-domain range, and every aggregate range in the authoritative domain document declared by the project documentation entry.

## Stability

- **ERROR-APPEND-001** **[REQUIRED][TOPIC]** — Append new errors in local-code order within the owning range.
- **ERROR-STABILITY-002** **[REQUIRED][TOPIC]** — Published error codes are immutable and must not be modified, reused, or assigned to a different meaning.
- **ERROR-MESSAGE-TEMPLATE-001** **[REQUIRED][TOPIC]** — Bind each error code to one stable message template; exception call sites must not replace it, concatenate ad hoc text, or supply an arbitrary message. Dynamic context may fill only parameters declared by the template and must not change the error meaning.
- **ERROR-MESSAGE-LANGUAGE-001** **[REQUIRED][TOPIC]** — Write stable business-error message templates in Chinese; Jakarta Bean Validation and technical-precondition diagnostics follow the validation-message rule.
