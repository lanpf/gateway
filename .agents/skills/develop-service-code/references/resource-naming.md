# Resource Naming Rules

## Resource names and namespaces

- **NAME-NAMESPACE-001** — Resource names, including but not limited to cache keys and distributed-lock keys, are scoped by a namespace: the effective name consists of the namespace prefix plus the business key, and the namespace must distinguish the application to prevent cross-application interference; environment isolation is guaranteed by the deployment infrastructure and is not a namespace responsibility.
- **NAME-NAMESPACE-002** — A business key is unique only within its namespace; the same business key resolves to a different physical resource in each application.
- **NAME-NAMESPACE-003** — The namespace expresses application identity only and never carries business meaning; business meaning appears only in the business-key part.
- **NAME-NAMESPACE-004** — Business code works only with business keys and must not perceive, parse, or hand-assemble an effective name's namespace prefix.
- **NAME-KEY-RESOLVER-001** — Name each concrete scenario resource-key handler `*KeyResolver` and extend `AbstractKeyResolver`; it owns only the business prefix and business-key segments and delegates resolution to its injected `ResourceNameResolver`.
- **NAME-RESOURCE-001** — Resolve every external resource name or key through `ResourceNameResolver`; inject its namespace either during scenario assembly or at the final resource-adapter boundary.
- **NAME-RESOURCE-002** — Business code, configuration files, and adapters must not hand-assemble application, environment, or other namespace prefixes.
- **NAME-RESOURCE-003** — For scenario-assembly namespace injection, make the scenario configuration implement `Namespaced` and inject into its `*KeyResolver` a `NamespacedResourceNameResolver` assembled from that configuration and `NamespaceResolver`.
- **NAME-RESOURCE-004** — For final-adapter namespace injection, give the `*KeyResolver` a `ResourceNameResolver` implementation that does not inject a namespace and let the final adapter provide namespace isolation.
- **NAME-RESOURCE-NAMESPACE-ONCE-001** — Inject a resource namespace exactly once; never combine `NamespacedResourceNameResolver` with a final resource adapter that injects the namespace again.
- **NAME-RESOURCE-005** — Framework entries that resolve resource names internally, such as `LockExecutor` for lock keys, perform namespace injection and key-segment normalization themselves; callers pass stable business key segments directly to the framework entry, create no `*KeyResolver` for them, and must not additionally resolve or inject the namespace.
