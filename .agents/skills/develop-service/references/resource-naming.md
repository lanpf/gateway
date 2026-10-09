# Resource Key and Namespace Rules

## Resource names and namespaces

- **NAME-RESOURCE-SCOPE-001** **[REQUIRED][TOPIC]** — Apply resource-key and namespace rules to business services; framework-internal modules follow their own engineering documentation.
- **NAME-NAMESPACE-001** **[REQUIRED][TOPIC]** — Compose each effective resource name from an application-distinguishing namespace and a business key; deployment infrastructure, not this namespace, provides environment isolation.
- **NAME-NAMESPACE-002** **[REQUIRED][TOPIC]** — A business key is unique only within its namespace; the same business key resolves to a different physical resource in each application.
- **NAME-NAMESPACE-003** **[REQUIRED][TOPIC]** — The namespace expresses application identity only and never carries business meaning; business meaning appears only in the business-key part.
- **NAME-BUSINESS-KEY-BOUNDARY-001** **[REQUIRED][TOPIC]** — Business code uses business keys only. A scenario `*KeyResolver` owns business prefixes and segments and delegates to injected `ResourceNameResolver`, without parsing or assembling namespaces. Exactly-once namespace injection follows the namespace ownership rules.
- **NAME-NAMESPACE-CONFIG-001** **[REQUIRED][TOPIC]** — Unified infrastructure configuration supplies application namespace identity.
- **NAME-NAMESPACE-MANUAL-PREFIX-001** **[REQUIRED][TOPIC]** — Business code, business configuration, and adapters must not repeat or hand-assemble namespace prefixes for an individual resource; the canonical exactly-once namespace rule owns injection count.
- **NAME-RESOURCE-003** **[REQUIRED][TOPIC]** — For scenario-assembly namespace injection, make the scenario configuration implement `Namespaced` and inject into its `*KeyResolver` a `NamespacedResourceNameResolver` assembled from that configuration and `NamespaceResolver`.
- **NAME-RESOURCE-004** **[REQUIRED][TOPIC]** — For final-adapter namespace injection, give the `*KeyResolver` a `ResourceNameResolver` implementation that does not inject a namespace and let the final adapter provide namespace isolation.
- **NAME-RESOURCE-NAMESPACE-ONCE-001** **[REQUIRED][TOPIC]** — Inject a resource namespace exactly once; never combine `NamespacedResourceNameResolver` with a final resource adapter that injects the namespace again.
- **NAME-RESOURCE-005** **[REQUIRED][TOPIC]** — When a framework entry already resolves a resource name, callers must not resolve or inject its namespace again; distributed-lock key behavior follows the lock rules.
