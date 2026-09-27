<!-- engineering-standards:begin version=3.1.7 -->
## Shared engineering guidance

Rule markers: [REQUIRED] is release-blocking, [DEFAULT] applies unless a concrete deviation reason is recorded, [ADVISORY] is optional guidance; [BASELINE] rules are preloaded for every change, [TOPIC] rules apply when the affected capability matches.

- **STD-HIERARCHY-001** **[REQUIRED][BASELINE]** — Shared rules apply to every project; service rules additionally apply to service projects, and project-specific authoritative documents may only add or tighten constraints. Preload and recheck baseline rules, but apply conditional rules only when their type or technology is relevant; never introduce unnecessary types, modules, or dependencies solely to satisfy an inapplicable rule.
- **STD-DOCS-001** **[REQUIRED][BASELINE]** — Before changing a service, read the project documentation entry declared by its project guidance, then read every authoritative document routed for the task.
- **STD-DOMAIN-002** **[REQUIRED][BASELINE]** — Before changing domain boundaries, language, business rules, errors, domain events, or API business semantics, read the service's authoritative domain document.
- **JAVA-VERSION-001** **[REQUIRED][BASELINE]** — Use Java 17.
- **DEP-CYCLE-001** **[REQUIRED][BASELINE]** — Module dependency cycles are forbidden; fix responsibilities or extract a shared module rather than hiding a cycle through reflection or events.
- **ARCH-DIRECTION-001** **[REQUIRED][BASELINE]** — Inner modules must not depend on outer protocols, persistence implementations, persistence objects, or the boot module.
- **ARCH-RESPONSIBILITY-001** **[REQUIRED][BASELINE]** — Keep domain rules in domain, use-case orchestration in application, technical adapters in infrastructure, protocol handling in interfaces, and runtime assembly in boot.
- **ERROR-STABILITY-001** **[REQUIRED][BASELINE]** — Never modify, reuse, or reassign a published error code.
- **VERIFY-CHANGE-001** **[REQUIRED][BASELINE]** — Run verification proportional to the change and do not claim completion without reporting the commands and results.

## Skill routing

- Developing, changing, or reviewing service code under the shared engineering standards during development: use `$develop-service`.
- Implementing or reviewing cross-instance locking, reliable event publication, message consumption, partitioning, dead letters, delayed messages, or related failure handling.: use `$develop-distributed`.
- Designing, implementing, or reviewing compensation, reconciliation, repair, cleanup, batch recovery, or fallback workflows: use `$develop-compensation`.
- Writing, running, or reviewing integration tests in the smoke-test phase: use `$test-integration`.
- Planning or applying coordinated standards-driven layered service refactors: use `$refactor-service`.

<!-- engineering-standards:end -->

# Project documentation

- Before making any change, read the root `README.md` for the project scope, module map, startup commands, and documentation routing.
- Before changing service responsibilities, cross-service collaboration contracts, trust boundaries, or delivery status statements, also read `docs/RESPONSIBILITIES.md`.
- Before changing domain boundaries, language, business rules, error definitions, policy models, or API business semantics, also read `docs/DOMAIN.md`.
- Read every additional authoritative document routed by the root `README.md` that applies to the task.
- If a change crosses multiple documented areas, read all applicable authoritative documents.
- The root `README.md` is the project entry and documentation index; the routed detailed documents are authoritative within their respective scopes.
