# gateway 领域设计 · v1

本文是 v1 技术方案的领域设计权威来源。职责与交付见 [RESPONSIBILITIES.md](RESPONSIBILITIES.md)，运行配置见 [README.md](../README.md)。

一次请求的生命周期分为两个宏观阶段：**阶段一入口准入**与**阶段二下游治理**。v1 交付聚焦阶段一；阶段二的能力语义在本篇保留为权威设计，实现与验收随后续版本交付。

## 1. 请求生命周期：两阶段与能力库

### 1.1 阶段一：入口准入（v1 交付）

决定放不放行、以什么身份放行、扣多少额度，全部发生在代理之前。三段固定管道：

1. **准备段（prepare）**：捕获单次快照与原始请求事实，取得技术路由候选，执行认证前 IP 限流。此段不依赖任何身份——未知路径与歧义候选同样受保护。
2. **接入段（access）**：主体认证与客户端签名验证协作，端点/App/Client/Subject 接入评估，产生接入决策与获准上下文。
3. **准入段（admit）**：使用获准上下文解析能力绑定，产生 ResolvedCapabilities，执行认证后限流，定稿 Header 准备（凭据清除计划与可信 Header 取值）。

### 1.2 阶段二：下游治理（语义权威，v1 暂不交付）

只作用于获准请求：缓存读取、熔断许可、超时熔断执行、Header 转发应用、缓存写入/受控降级、响应生命周期（超时、缓冲、取消、连接释放）。被拒绝的请求不产生任何下游动作；认证拒绝、网关限流、缓存命中不计入熔断统计——它们不是下游故障。

**交接物**：入口准入向下游治理移交获准上下文（AccessGrantedContext）、ResolvedCapabilities（限流、超时、熔断、缓存、Header 各节点）、配额执行记录、Header 准备结果与捕获的原始证据。缓存键使用清除前的请求证据；Header 安全清除属转发必需项，随阶段一交付。

### 1.3 能力库与固定拓扑

能力是语义原子单元，每个能力定义输入事实、输出事实、失败语义与放置槽位：

| 能力 | 放置 | 阶段 | v1 交付 |
| --- | --- | --- | --- |
| snapshotCapture | 准备段 | 一 | 是 |
| endpointMatch | 准备段 | 一 | 是 |
| rateLimit（pre-auth 槽位，仅 IP 维度） | 准备段 | 一 | 是（EXACT） |
| subjectAuthentication | 接入段 | 一 | 是 |
| clientVerification（签名 + 防重放） | 接入段 | 一 | 是 |
| appAccess / clientAccess / endpointAccess | 接入段 | 一 | 是 |
| capabilityBinding 解析 | 准备段 / 准入段，统一能力服务按当前可信事实解析 | 一 | 是（阶段二节点仅作为交接物携带） |
| rateLimit（post-auth 槽位，身份维度及新命中的身份绑定 IP 规则） | 准入段 | 一 | 是（EXACT） |
| headerPreparation | 准入段 | 一 | 是 |
| headerApply（凭据清除 + 可信写入） | 下游治理 | 二 | 是（安全必需） |
| responseCache | 下游治理 | 二 | 暂缓 |
| circuitBreaker | 下游治理 | 二 | 暂缓 |
| timeout | 下游治理 | 二 | 暂缓 |
| staleDegrade | 下游治理 | 二 | 暂缓 |

限流是一个能力、两个槽位：IP 规则最早可在 pre-auth 执行；身份维度或带身份绑定的 IP 规则必须等待 post-auth。同一规则跨阶段最多执行一次，支持两个槽位不代表每条规则执行两次。执行拓扑固定、不可由调用者指定，原因有三：

- **顺序编码安全不变量**，违反时故障静默：缓存先于授权即绕过认证；Header 写入先于认证即透传伪造身份；IP 限流后置即失去未知路径保护。
- **能力间存在数据依赖**：认证后能力消费获准上下文，自由排序是幻觉。
- **接入评估的子序是探测 oracle**：顺序泄露给未认证调用方即成为信息通道。

组合需求由数据层承接（Bundle/Binding，见第 3 节）。固定链实例不止一条：**入口链**（上述拓扑）与**管理链**（第 4.4 节），两者复用能力库但拓扑各自固定。

## 2. 统一语言与模型

本节是词汇表与模型契约。2.1–2.6 按领域分区列出模型，每条只写"它是什么、不变量由谁持有"；2.7 给出跨模型规则，2.8 展开接入评估的固定顺序。

### 2.1 登记载体与聚合

| 模型 | 定义 |
| --- | --- |
| App / AppId | 已登记应用载体。声明 App 不等于证明身份。 |
| AppAccess | App 的接入属性值对象，当前仅含规范化 allowedOrigins。 |
| AppRegistry | App 聚合的不可变注册表；重复 AppId 在构造时以 APP_AMBIGUOUS 拒绝。 |
| Client / ClientId | 接入主体。 |
| ClientAccess | Client 的接入属性值对象：allowedApps 与可选 Credentials。 |
| ClientRegistry | Client 聚合的不可变注册表；重复 ClientId 在构造时以 CLIENT_AMBIGUOUS 拒绝。 |
| Endpoint / EndpointId | 端点。拥有逻辑服务归属、逻辑交付路由、端点匹配器。 |
| EndpointMatcher |(httpMethod, pathPattern) 精确匹配器；method 大写化，两者均去除首尾空白。
| EndpointAccess | 端点接入策略：clientVerificationRequirement、subjectAuthenticationRequirement、requiredScopes、requiredAudiences。 |
| EndpointRegistry | Endpoint 聚合的不可变注册表；重复 EndpointId 或重复 EndpointMatcher 在构造时以 ENDPOINT_AMBIGUOUS 拒绝。 |

### 2.2 凭据与签名协议

| 模型 | 定义 |
| --- | --- |
| Credential / CredentialId | 归属于 Client 的签名凭据值对象，内嵌于 ClientAccess；含 secretRef、signatureContract、validFrom/expiresAt，validFrom 必须早于 expiresAt。 |
| Credentials | active 与至多一个 grace 凭据，表达密钥轮换；两者必须同一协议且 credentialId 不同。 |
| SignatureContract | 四种签名协议枚举；协议由受管理凭据指定，不能由请求自由选择或失败后降级。 |

### 2.3 主体与认证

| 模型 | 定义 |
| --- | --- |
| SubjectType | HOST_SESSION / BROWSER_SESSION；会话家族由 interfaces 按请求传输证据选择。 |
| SubjectId / SessionId | 主体标识与会话标识值对象，非空白并去除首尾空白。 |
| AuthenticatedSubject | 认证成功的主体事实：subjectType、subjectId、sessionId、issuer、audiences、scopes、authenticationExpiresAt。 |
| SubjectAuthenticationOutcome | Authenticated(subject) / Absent / Rejected(reason) / Failed(reason)。 |
| SubjectAuthenticationRejectReason | EXPIRED / INVALID。 |
| SubjectAuthenticationFailReason | UPSTREAM_UNAVAILABLE / RESPONSE_INVALID。 |

### 2.4 请求事实与接入评估

| 模型 | 定义 |
| --- | --- |
| RequestEvidence | 一次性捕获的原始请求事实；后续阶段只消费，不重新采集。 |
| AccessRequest | 接入评估的输入事实：endpointId、appId 必填，clientId、origin、unsafe 可空或派生。 |
| VerifiedApp | 获准上下文携带的应用事实，仅含 appId 与非空白 platform。 |
| VerifiedClient | 已验证的 Client 事实：clientId、credentialId、verificationMode、verifiedAt，四者必填。 |
| ClientVerificationMode | 当前仅 REQUEST_SIGNATURE。 |
| ClientVerificationOutcome | Verified(client) / Absent / Rejected / Failed，与主体认证结局同构。 |
| AccessEvaluator | 无状态纯规则：依快照与两个验证/认证结局产生 AccessDecision，详见 2.8。 |
| AccessDecision | Allowed(grantedContext) / Denied(denyReason)；两种结局都携带请求所持 SnapshotVersion。 |
| AccessDenyReason | 十六种拒绝原因；禁用与不存在共用 NOT_FOUND 系列，不另设禁用原因。 |
| AccessGrantedContext | 仅含 app（VerifiedApp，必填）、client（VerifiedClient，可空）、subject（AuthenticatedSubject，可空）。 |

### 2.5 快照与版本

| 模型 | 定义 |
| --- | --- |
| Revision | Definition 文件级源修订令牌，非空白字符串包装；不透明，只用于 CAS 比较。 |
| SnapshotVersion | 全局发布版本，正数 long 包装；0 非法。 |
| IngressContent | 快照内容：App / Client / Endpoint 三个注册表与 CapabilityComposition。 |
| IngressSnapshot | 唯一版本化的不可变入口快照：version、content、contentHash、publishedAt。 |

### 2.6 策略组合

| 模型 | 定义 |
| --- | --- |
| CapabilityComposition | 同一快照内 Bundle 与 Binding 的完整配置，负责引用及竞争节点校验。 |
| CapabilityBundle / CapabilityBundleId | 能力节点容器：限流、超时、熔断、缓存、Header，每种恰好零或一份。 |
| CapabilityBinding / CapabilityBindingId | 按条件（serviceId/endpointId/tags + appId/verifiedClientId/subjectType）把 Bundle 绑定到端点。 |
| ResolvedCapabilities | 绑定解析结果：rateLimits、timeout、circuitBreaker、cache、headers。 |
| CapabilityResolver | 无状态解析器；按当前可信事实解析完整 ResolvedCapabilities，不提供限流专用的 preAuthRules。 |
| CapabilityContext / CapabilityFact | 当前槽位、请求事实、可空端点及获准上下文；事实可用性由上下文推导，区分尚未认证与接入完成但无主体。 |
| CapabilityDefinition / CapabilityUnit | 能力声明支持槽位、重复策略及所需事实；能力实现生成强类型执行单元，单元也可声明额外事实依赖。 |
| CapabilityPlanner / CapabilityPlan | 按固定注册顺序生成执行计划，校验槽位／事实并按执行键去重，不依赖具体限流类型。 |
| CapabilityExecution / CapabilityExecutionKey | 累计执行键与当前槽位强类型结果；键为能力标识 + 单元标识，按槽位重复时额外包含槽位。 |
| TimeoutPolicy / CircuitBreakerPolicy / CachePolicy / HeaderPolicy | 单值完整配置节点，随 Bundle 整体覆盖，无独立身份、无逐请求决策。 |
| RateLimitRule / RateLimitRuleId / RateLimitRules | 限流的多值可寻址规则：Rule 有独立身份构成配额键、按维度落到 pre-auth/post-auth 槽位；Rules 是 Bundle 的完整限流节点（规则组 + Redis 故障策略）。 |
| ManagementAccessRules | 管理入口的最低要求（HOST_SESSION + admin scope + 配置 audience）；启动时校验当前策略，运行期独立执行同一规则。 |

### 2.7 跨模型规则

**标识与匹配**

- `serviceId` 是逻辑服务归属，`routeId` 是逻辑交付路由，SCG `route.id` 直接作为技术实现。
- 同一服务可以因前缀改写或转发目标不同拥有多条路由；治理策略不同不要求拆路由。
- 端点匹配器以 (method, pathPattern) 精确字符串在注册表内唯一，不能任意选第一条。
- `tags` 是受管理的分类，不来自请求，可为空。

**App**

- 拥有 `AppStatus`（ENABLED/DISABLED）。
- `platform` 仅来自配置，是可修改的受管理属性，必须非空白并去除首尾空白；更正 platform 不要求重新登记 App 或重建 Client 授权，保存只更新 Definition，显式发布后生效。
- `allowedOrigins` 是规范化完整 HTTP Origin：scheme 与 host 小写、默认端口省略；不接受通配符、path、userinfo、query 或 fragment；仅接受 http/https。用于 Browser Session 非安全请求校验，不自动开启 CORS。

**Client 与凭据**

- `ClientAccess` 定义 `allowedApps` 与可选 `Credentials`；`ClientStatus` 位于 Client 聚合。
- `Credentials` 包含 active 与至多一个 grace 凭据，两者必须同一签名协议且 `credentialId` 不同。
- `credentials` 为 null 表示未配置凭据：该 Client 不能通过签名验证，也不参与快照凭据校验。
- 凭据通过 `secretRef` 解析密钥；`credentialId` 在快照内唯一。
- 请求声明只定位配置；验证并完成防重放后才形成已验证 Client 身份，未验证的声明不产生 `VerifiedClient`。
- 一个 Client 可使用多个受允许的 App。

**Endpoint 接入策略**

- `EndpointAccess` 由四个字段构成：`clientVerificationRequirement`、`subjectAuthenticationRequirement`（必填），`requiredScopes`、`requiredAudiences`（可空集合）。
- 使用 `EndpointStatus`（ENABLED/DISABLED），与 `AppStatus`、`ClientStatus` 保持显式状态建模；`enabled()` 仅为领域便捷判断，不是配置字段。
- v1 聚焦入口访问控制，凭据转发设置不再属于端点，由 `HeaderPolicy` 承载。

**clientVerificationRequirement（REQUIRED/OPTIONAL）**——对验证结果的策略：

- REQUIRED：必须声明 Client，且 Client 验证结果必须为 Verified。
- OPTIONAL：允许不声明 Client；一旦声明，仍检查登记、状态及 App 授权。
- 无论端点要求如何，提交的验证结果一律按结果处理：Verified 必须与声明一致，否则 `CLIENT_VERIFICATION_MISMATCH`；未声明却携带验证结果同样 MISMATCH；Rejected 拒绝；Failed（验证依赖不可用）拒绝，绝不置空放行。
- 不再提供 FORBIDDEN、端点级验证模式（IGNORE/REQUEST_SIGNATURE）及"声明但不验签"组合；签名验证只能由受管理凭据的协议发起，不能由请求自由选择或失败后降级。

**subjectAuthenticationRequirement（REQUIRED/ANONYMOUS）**——对认证结果的策略：

- REQUIRED：必须提供已认证主体。
- ANONYMOUS：未提交 Token（Absent）匿名放行；已证明可信、仅过期的 Token（EXPIRED）视同未提交并放行；其余拒绝原因（INVALID）拒绝。不能仅根据客户端声明的 exp 或未经完整验证的错误将结果分类为 EXPIRED；认证适配器不能证明时必须拒绝。
- 认证依赖不可用（UPSTREAM_UNAVAILABLE）与响应非法（RESPONSE_INVALID）一律拒绝，不能降级匿名或使用缓存身份。
- 端点不再声明 Host/Browser 会话类型；会话家族由 interfaces 层按请求传输证据选择（Bearer token → Host，BROWSER_SESSION Cookie → Browser），Browser Session 不得成为管理主体。管理入口最低要求（REQUIRED + admin scope + 配置 audience）由 `ManagementAccessRules` 表达。

**requiredScopes / requiredAudiences**

- null 归一为空集合，非空元素必须非空白，保存为不可变集合。
- 配置非空时，主体必须包含全部指定值（子集 AND 校验）。
- Browser Session 不产生 OAuth2 scope/audience（集合恒为空），为其配置非空要求即不可满足。
- 空集合不关闭 Token 认证阶段的 audience 校验，也不放宽管理入口的最低要求。

**认证结局**：区分缺失（Absent）、认证成功（Authenticated）、拒绝（Rejected）、依赖不可用（Failed）；Client 验证结局同构。`sessionId` 重命名不改变既有会话标识语义。`authenticationExpiresAt` 仅描述认证到期信息，不是会话绝对期限或到期前不会撤销的保证，目前无后续治理消费者。

**AuthenticatedSubject 的校验按 subjectType 分支**。两种主体共同要求：`subjectType`、非空白 `subjectId`/`sessionId`、`authenticationExpiresAt`；`subjectId`/`sessionId` 去除首尾空白。分支差异：

| 校验项 | HOST_SESSION | BROWSER_SESSION |
| --- | --- | --- |
| issuer | 必填非空白，去除首尾空白 | 必须为空：非空白即拒绝，最终归一为 null |
| audiences / scopes | 允许 null 或空集合；null 归一为空集合，元素非空白，集合不可变 | 必须为空：非空即拒绝，最终归一为空集合 |

不能在分支前统一要求 audiences/scopes 非空。

**AccessGrantedContext** 仅含三个字段：

| 字段 | 类型 | 可空 |
| --- | --- | --- |
| app | VerifiedApp | 否 |
| client | VerifiedClient | 是 |
| subject | AuthenticatedSubject | 是 |

- `VerifiedClient` 含 `clientId`、`credentialId`、`verificationMode`、`verifiedAt`，只表达已通过凭据验证的 Client；请求声明的 Client 只作为入口校验输入，不进入获准上下文。
- v1 决定直接复用完整 `AuthenticatedSubject`（保留 issuer 与 authenticationExpiresAt），不构造裁剪后的 `SubjectContext` 值对象；上下文的可信性来自认证及接入评估流程，字段完整不等于认证成功。
- 快照版本属于请求所持快照，记录在 `AccessDecision` 上，不放入身份上下文。

### 2.8 接入评估的固定顺序

`AccessEvaluator` 是无状态纯规则：输入为快照、`AccessRequest` 与两个结局（Client 验证、主体认证），输出 `AccessDecision`。前置条件由调用方保证——端点必须已唯一匹配、appId 必须已声明，否则命令服务在进入评估前即拒绝，评估器不处理这两种缺失。

评估顺序固定，先拒绝者胜出，后续规则不再执行：

1. **端点**：按 endpointId 查注册表并要求 ENABLED，否则 ENDPOINT_NOT_FOUND。
2. **App**：按 appId 查注册表并要求 ENABLED，否则 APP_NOT_FOUND；通过后立即固化为 VerifiedApp。
3. **Client**：子序见下。
4. **Subject**：子序见下。
5. **Browser Origin**：仅当主体为已认证的 BROWSER_SESSION 且请求为非安全方法时，要求命中 App 的 allowedOrigins，否则 BROWSER_ORIGIN_NOT_ALLOWED。

**Client 子序**——登记与 App 授权先于验证结果策略：

| 条件 | 结果 |
| --- | --- |
| 未声明 ClientId，端点 REQUIRED | 拒绝 CLIENT_REQUIRED |
| 未声明 ClientId，端点非 REQUIRED，却携带非 Absent 的验证结果 | 拒绝 CLIENT_VERIFICATION_MISMATCH |
| 未声明 ClientId，端点非 REQUIRED，验证结局 Absent | 放行 |
| 已声明 ClientId，注册表查不到或非 ENABLED | 拒绝 CLIENT_NOT_FOUND |
| 已声明 ClientId，Client 不允许该 App | 拒绝 CLIENT_NOT_ALLOWED |
| 已声明，验证结局 Absent 且端点 REQUIRED | 拒绝 CLIENT_VERIFICATION_REQUIRED |
| 已声明，验证结局 Absent 且端点 OPTIONAL | 允许，但不生成 VerifiedClient，不参与 Client 身份绑定或配额 |
| 已声明，验证结局 Verified 但 clientId 与声明不一致 | 拒绝 CLIENT_VERIFICATION_MISMATCH |
| 已声明，验证结局 Rejected | 拒绝 CLIENT_VERIFICATION_REJECTED |
| 已声明，验证结局 Failed | 拒绝 CLIENT_VERIFICATION_FAILED |

**Subject 子序**——先 audience 后 scope：

| 条件 | 结果 |
| --- | --- |
| Absent 且端点 REQUIRED | 拒绝 SUBJECT_REQUIRED |
| Authenticated，audiences 未包含全部 requiredAudiences | 拒绝 AUDIENCE_NOT_ALLOWED |
| Authenticated，scopes 未包含全部 requiredScopes | 拒绝 SCOPE_NOT_ALLOWED |
| Rejected（EXPIRED）且端点 ANONYMOUS | 放行（视同未提交） |
| Rejected，其余原因 | 拒绝 SUBJECT_AUTHENTICATION_REJECTED |
| Failed（UPSTREAM_UNAVAILABLE） | 拒绝 SUBJECT_AUTHENTICATION_UPSTREAM_UNAVAILABLE |
| Failed（RESPONSE_INVALID） | 拒绝 SUBJECT_AUTHENTICATION_RESPONSE_INVALID |

只有本次端点规则实际接纳的验证/认证结果才进入获准上下文：`VerifiedClient` 仅在验证结局为 Verified 时携带，`AuthenticatedSubject` 仅在认证结局为 Authenticated 时携带。

## 3. 策略组合与匹配

本组合是数据层组合机制，服务准备段与准入段的绑定解析，以及阶段二的能力节点；两个槽位由 CapabilityExecutionCommandService 统一编排。v1 保留模型与发布校验，阶段二节点仅作为交接物携带。

**Policy 与 Rule 的分界**：

| 后缀 | 语义 | 身份 | 合并方式 |
| --- | --- | --- | --- |
| `*Policy`（Timeout / CircuitBreaker / Cache / Header） | 单值完整配置节点 | 每种能力在 Bundle 内恰好零或一份 | 被 Binding 整体覆盖；不产出逐请求决策 |
| `RateLimitRule` | 可独立执行的可寻址单元 | 有独立身份，构成配额键 | 可累加、跨阶段去重 |

**Policy 被覆盖，Rule 被执行**——这一差异决定了两个能力族的合并语义不同（见第 4.1 节）。

**命名约定**：治理能力的组合与解析使用 `Capability*` 词族，解析结果使用 `ResolvedCapabilities`；单值能力配置节点保留 `*Policy` 后缀，不指业务授权。限流使用 `RateLimitRules` 表达规则组，不属于单值 Policy 节点。

| 命名形式 | 含义 | 成员 |
| --- | --- | --- |
| 前缀 `Capability*` | **组合与执行机制**——绑定、解析、规划及执行记录 | `CapabilityComposition`、`CapabilityBundle`、`CapabilityBinding`、`CapabilityResolver`；`policy.execution` 下的槽位、规划与记录模型 |
| `ResolvedCapabilities` | **解析结果**——当前请求适用的能力配置 | 累加后的限流规则组，以及覆盖解析后的超时、熔断、缓存、Header 节点 |
| 后缀 `*Policy` | **单个能力节点** | `TimeoutPolicy`、`CircuitBreakerPolicy`、`CachePolicy`、`HeaderPolicy` |

与治理无关的同名标识**不属于本词族**，读到时按各自领域理解：`RedisFailurePolicy` 是限流依赖故障时的处置方式，`LocalPolicyWebFilter` / `PolicyAwareBearerTokenConverter` 中的 Policy 指"本次请求的准入策略"。

**为什么单值节点保留 `*Policy` 后缀**：后缀区分**配置与配置对象**。`CachePolicy` 是"怎么缓存"的设置，`Cache` 则是缓存本身；`TimeoutPolicy` 是连接/等待/总时限，`Timeout` 则是超时事件。去掉后缀会让名字指向被配置的东西而不是配置。注意这不构成一条统一约定——限流节点叫 `RateLimitRules`、不带后缀，因为它是**规则组**而非单值配置（见上表）。

`CapabilityBinding` 通过 `bundleId` 引用同一快照内的 `CapabilityBundle`。编辑 Bundle 不影响运行请求，显式发布后影响全部引用它的 Binding；运行时不查询最新编辑态。

**绑定优先级**：全局 < 服务 < 标签 < 端点。

- 可附加 `AppId`、已验证 `ClientId`、`SubjectType` 条件；条件采用 AND，标签集合全部满足。
- 上下文条件不额外提高优先级。
- 没有任意脚本。

**发布校验**：

- 校验引用、范围与受影响端点。
- 同层可同时命中且竞争同一节点的绑定拒绝发布。
- 限流累加而非覆盖，同一 Bundle 重复引用只执行一次限流。

**空节点**表示不声明该能力，不能用空值关闭继承能力；需要例外时应收窄上层绑定范围或调整受管理标签。

## 4. 阶段一：入口准入

### 4.1 准备段（prepare）

**入口**：
- 唯一概念入口是application 的 `PreparationStageCommandService.prepare`，HTTP 中性、返回 CompletionStage；命令为 `PreparationStageCommand`，仅包含 `RequestEvidence`——命令就是"请携带这份证据"，不新增任何事实。
- 协议入口是 interfaces 的 `IngressPreparationWebFilter`——运行链中唯一调用方（也是 `RequestEvidence` 的唯一生产方），位于安全链与 SCG 路由分发之前，每请求恰好执行一次（完成标记防重入）。过滤器注入准备段服务，不逐请求装配应用服务。

**入参（RequestEvidence，一次性捕获的完整请求事实）**：
- method：大写化、安全方法集为 GET/HEAD/OPTIONS/TRACE。
- rawPath/rawQuery：不解码不改写；null = 无查询，空串 = 空 query，二者对签名语义有区别。
- 声明 AppId/ClientId：可空。
- Origin：可空。
- remoteIp：限流用 IP，由 interfaces 按部署配置解析，v1 取 remote address，X-Forwarded-For 归部署侧）
- 签名证据材料**不进入准备段**：是否采集取决于匹配后的端点策略，1 MiB/413 规则属接入段签名验证。

**流程（三步，对应 1.3 能力库的准备段能力）**：

1. **snapshotCapture**：读取当前快照，恰好一次（`AccessSnapshotCapturer` 端口；捕获现有版本，不创建新版本）。运行期不存在无快照状态——首次启动无有效快照已由启动失败保证，LKG 保证降级有界。
2. **endpointMatch**：产出候选并归类。**路径语法是适配器事实，胜者选择是领域规则**。
   - 适配器：`EndpointMatchSource` 端口，interfaces 以 SCG PathPattern 实现。
   - 归类：domain 依 `EndpointMatch` 判定 UNIQUE / NONE / AMBIGUOUS。
   - 折叠：相同候选折叠为单一——端点被自己的多个匹配器命中时以 [E, E] 到达。
   - 歧义：同 id 但内容分歧、或两个不同端点命中。
   - 保护范围：只有唯一候选贡献端点级保护；歧义与未知走全局规则，不向调用方泄露差异。
3. **能力执行（PRE_AUTH 槽位）**：
   - 入口：准备段只构造 `CapabilityExecutionCommand(composition, context, executed)` 并调用 `CapabilityExecutionCommandService.execute(command)`；配置来自已捕获快照，首次执行键为空。以下解析、规划与执行均由该应用服务统一编排，阶段不分别持有 Resolver/Planner。
   - 解析：`CapabilityResolver.resolve(composition, context)` 只使用当前可信事实；认证前不使用声明 App/Client 作为绑定条件。null 端点仅匹配全局无身份绑定。
   - 规划：`CapabilityPlanner` 检查能力声明的槽位与所需事实，再选择执行单元；`RateLimitCapability` 只产生可用 IP 规则的强类型单元。
   - 执行：按固定注册顺序执行批次；能力内部聚合，能力之间遇拒绝短路，取消沿协作链传播。统一返回 `CapabilityExecutionOutput(capabilities, execution)`，包含本次解析结果及执行记录；拒绝同样保留解析结果，异常不伪造允许输出。

**通用执行契约**：`CapabilityRepeat.ONCE_PER_REQUEST` 的键为能力标识 + 单元标识；`ONCE_PER_SLOT` 额外包含槽位。不同能力可复用单元名称，互不干扰。仅完成的批次写入执行记录（拒绝也是完成）；不记录缺失事实、未匹配、取消或未执行单元。规则不得通过配置调整安全执行顺序。新增能力需提供领域定义、强类型单元与结果、应用执行器及必要的 HTTP 结果投影，在装配处固定登记，不修改准备／准入编排。本期只登记限流，Header 应用仍走既有固定安全步骤，缓存／超时／熔断不因此启用。

**限流（两槽位共享语义）**：

| 事项 | v1 语义 |
| --- | --- |
| 交付模式 | 只交付 EXACT；PREALLOCATED 为后续模式，不允许启用或静默替换 |
| 扣减 | Redis 原子令牌桶，每次扣减共享额度 |
| 多规则 | 多条适用规则全部执行，相同维度不能只执行第一条 |
| 槽位覆盖 | 认证前 IP 保护覆盖未知路径；认证后执行身份维度及新匹配的身份绑定 IP 规则 |
| 维度约束 | 公开 App 声明不能作为唯一防滥用维度 |
| 超限 | 429 + Retry-After |
| Redis 故障 | 按策略 CLOSED（503）或 OPEN_WITH_LOCAL（有界本地配额、告警，不承诺集群精确） |
| 配额键 | 稳定规则身份构成配额键；不追加 snapshotVersion，避免每次发布重获满额 |

**RateLimitEvaluator 的职责边界**：

- 只决定适用规则与聚合结果：缺失／空白维度跳过；批次内按 `RateLimitRuleId` 去重。请求链的跨槽位去重由通用 `CapabilityPlanner` 负责。
- 聚合优先级：不可用优先于超限；超限取最大等待时间；全部允许时取最小剩余额度。
- `QuotaCommandService` 执行全部适用规则并记录结果，按领域故障规则协调共享额度与本地应急额度；技术适配器不再决定降级模式。
- 请求链通过 `PlannedQuotaCommand` 执行已规划批次；独立配额用例仍可使用 `QuotaCommand`，复用同一领域选择及批次执行逻辑，不参与入口槽位编排。

**拒绝位置**：准备段只拒绝限流失败（429/503）。歧义与未知端点以 EndpointMatch 归类交接，由接入段命令服务在构造 AccessRequest、触发认证之前统一拒绝 ENDPOINT_NOT_FOUND；不把空 endpointId 交给 AccessEvaluator。不提供专门歧义原因，歧义另计入低基数指标供部署侧告警，对内可见、对外统一。

**出口与交接**：输出为密封接口 `PreparationStageOutput`，两种结局靠类型区分而非调用方自觉。

| 结局 | 载荷 | 后续 |
| --- | --- | --- |
| `Prepared` | snapshot、endpointMatch、evidence、execution | 交接给接入段 |
| `Denied` | execution | 请求在边缘终止；当前限流投影为 429/503，不存在交接 |

- `execution` 是 `CapabilityExecution`：累计 `Set<CapabilityExecutionKey> executed` 与当前槽位 `List<CapabilityOutcome> outcomes`。限流结果为 `RateLimitCapability.Outcome`，过滤器通过注册的结果投影输出 HTTP，不识别规则和槽位。
- 规则身份为值对象 `RateLimitRuleId`（与 AppId/ClientId 同款），不再使用裸 String。
- 交接硬规则：接入段命令由 `Prepared` 构造；接口层不得重新采集事实或重读快照。
- SCG 路由核对发生在交接路径上、接入段之前（interfaces 职责）：唯一候选的 routeId 必须等于 SCG 实际命中路由 id，不一致拒绝。
- 请求取消经 `IngressTask` 级联终止未完成的限流协作。

### 4.2 接入段（access）

评估语义（接入顺序、验证/认证结果策略、拒绝原因、获准上下文构成）见第 2 节。本段定义认证协作与签名协议。

**入参**：本段的唯一入口是 `AccessStageCommandService.evaluate(command, subjectAuth)`。

| 输入 | 性质 | 来源 |
| --- | --- | --- |
| `command.prepared` | 事实（准备段的成功产出） | `PreparationStageOutput.Prepared`：单次快照、匹配归类、请求事实、配额执行记录 |
| `command.signatureEvidence` | 事实，可为 null | interfaces 在**匹配之后**采集——是否缓冲 body 取决于端点策略；null 表示未提交签名 |
| `subjectAuth` | 协作者（`Supplier`） | interfaces 提供的延迟认证入口 |

事实进 command、协作者走参数，是刻意分开的：事实有多个来源但都属于"本段需要知道什么"，协作者则不是事实。`subjectAuth` 用 `Supplier` 而非值，使歧义与未知路径直接拒绝而**不触发认证**（见 4.1 的拒绝位置）。

**阶段交接的统一规则**：每条边只携带上一段的**成功产出**——`Prepared`（拒绝在边缘终止，不进管道）与 `Allowed`（拒绝在接入段终止，不进准入段）。命令直接**包裹**上一段的产出而不是重述其字段，交接因此是带类型的：`AccessStageCommand` 不可能由准备段产出以外的东西构造。代价是命令会携带本段不读的字段（如配额记录），这是刻意的——它是"管道在那一刻知道什么"，而不是"本段需要什么"。

**认证协作**：本段自己收口——`evaluate` 内部完成客户端验证、主体认证的 join 与接入决策，返回密封的 `AccessStageOutput`。独立的 `IngressAdmissionCommandService` 在本段允许后调用准入段；阶段之间不直接持有或调用彼此。应用阶段只使用请求事实、领域对象和 CompletionStage，不接收 Spring Security 或 Reactor 类型；请求取消及异步失败终止其未完成协作。

**出口**：`AccessStageOutput` 的两个变体都持有 `AccessDecision`——`Denied(AccessDecision.Denied)` 与 `Allowed(AccessDecision.Allowed, prepared)`。允许结局继续携带原始 Prepared，确保准入段使用同一快照、端点与请求事实；本段不解析能力、不构造配额维度。拒绝结局原样带出。

**签名**：历史协议计算保持不变：

- LEGACY_SECRET_PARAMS_TIMESTAMP_MD5_UPPER：MD5(secret + params + timestamp) 大写，保留原 params 提取方式。
- LEGACY_SECRET_TIMESTAMP_BODY_SHA256：SHA256(secret + timestamp + body)。
- LEGACY_SECRET_TIMESTAMP_BODY_HMAC_SHA256：HMAC-SHA256(secret, timestamp + body)。

HTTP_REQUEST_SIGNATURE_V1 是独立新协议，覆盖原始 method、路径/查询、AppId、ClientId、credentialId、timestamp、nonce、body 摘要，固定编码及规范化规则；在改写前验证。协议由受管理凭据指定，不能由请求自由选择或失败后降级。

签名请求及 GET 请求的入口 body 证据采集上限为 1 MiB，超限返回 413；有 body 的 GET 不缓存。普通未签名非 GET 请求不由该证据采集器统一缓冲。

**新协议输入 Header**：`X-App-Id`、`X-Client-Id`、`X-Credential-Id`、`X-Timestamp`、`X-Nonce`、`X-Signature`。

**签名原文依次编码**：协议名 `HTTP_REQUEST_SIGNATURE_V1` → `method` → `rawPath` → `rawQuery` → `AppId` → `ClientId` → `credentialId` → `timestamp` → `nonce` → body 的 SHA-256 小写十六进制摘要。

- 每个字段使用 UTF-8 字节长度的 netstring：`长度:值,`；缺失 query 使用 `-1:,`，空 query 使用 `0:,`。
- 不解码、不排序、不重写原始路径/查询。
- `timestamp` 为规范十进制 epoch 毫秒。
- `nonce` 为 22–128 个 base64url 字符，由调用方用至少 128 bit 随机数生成。
- 签名是以 secret 为密钥、上述字节为消息的 HMAC-SHA256，小写十六进制 64 字符。
- 防重放按 Client + nonce 隔离，而非 `credentialId`——轮换不能绕过重放约束。

**密钥与候选选择**：

- secret 只通过 `secretRef` 解析，不写入快照/日志。
- 恒定时间比较；成功后原子登记防重放，登记失败不得建立身份。
- active/grace 只表示密钥轮换；新协议按 `credentialId` 精确选择有效候选，历史协议最多依序尝试两份。
- 凭据归属 Client 的身份由调用方传入并与证据核对。
- 协议迁移与密钥轮换分开。

凭据缺失或错误返回 401；密钥解析或防重放依赖不可用返回 503，不能转换为匿名或尝试旧协议兜底。

domain 定义签名原文、输入格式、候选选择、时间窗口和防重放身份；application 协调密钥读取、密码学端口和防重放登记。Redis 空响应属于依赖异常，不等同真实重复登记；请求取消向未完成的登记传播。

### 4.3 准入段（admit）

**入参**：`AdmissionStageCommand(AccessStageOutput.Allowed access)`——仅包裹接入段的**成功产出**。拒绝不进本段：它在接入段终止，由组合入口直接送到终态输出。

Allowed 包裹同一 Prepared，保留快照、请求事实与通用执行记录；接入段不修改记录，准入段从 `prepared.execution().executed()` 获取已执行键，不另传一份 applied。

准入段只有 `AdmissionStageCommandService.admit(AdmissionStageCommand)` 一个入口，输入只来自接入段的成功产出；不依赖 AccessStageCommandService，也不重新认证。它构造 POST_AUTH 的 CapabilityContext，并将同一快照的 composition 及 Prepared 的执行键交给 CapabilityExecutionCommandService；阶段不再分别调用解析器与规划器。

- **能力绑定解析（capabilityBinding）**：以获准上下文与端点解析 ResolvedCapabilities；Header 默认继承在 domain 完成（默认凭据转发为全剥离，无端点级默认值）。阶段二能力节点（超时、熔断、缓存）仅作为交接物携带。
- **能力执行（POST_AUTH 槽位）**：统一能力服务内部使用同一解析器与规划器；限流定义依据获准事实生成 App、已验证 Client、Subject、Endpoint 以及新匹配 IP 单元，通用规划器排除已执行键。维度生成不属于阶段服务。
- **headerPreparation**：按命中的 HeaderPolicy 定稿凭据清除计划与可信 Header 取值；固定安全规则不可关闭：先删除所有受保护字段，再写验证结果；来源缺失则不写入。语义详见第 6 节。

`AdmissionStageOutput(Allowed access, capabilities, execution)` 的三项均必填，只表达实际执行过准入段的评估结果；execution 仍可能拒绝。Header 计划由 `capabilities.headers()` 唯一承载，不复制第二份；获准上下文与原始证据保留在 access 中。

capabilities 与 execution 直接取自同一次 `CapabilityExecutionOutput`；Header 后续消费不重新解析。能力命令的三个字段均必填，执行键集合允许为空并防御性复制。Resolver/Planner 继续保留 domain 纯规则职责；应用服务只统一调用顺序、批次执行及取消／异常，不引入额外转发服务。

**组合入口**：`IngressAdmissionCommandService.admit(AccessStageCommand, subjectAuth)` 串联 access → admit，负责拒绝短路、已执行配额记录交接及跨阶段取消／异常传播。其返回 `IngressAdmissionOutput`：`AccessDenied` 包裹接入拒绝；`Evaluated` 包裹非空 AdmissionStageOutput，不等于配额已允许。接入拒绝不会构造准入段输出或调用准入段。

三个业务阶段对应两个协议接入点：准备过滤器在安全链与路由匹配之前；`IngressAdmissionGlobalFilter` 在 SCG 路由就绪后调用组合服务，处理 HTTP 响应与 Header 转发。过滤器及应用服务均在装配时连接，不逐请求创建；不是用三个过滤器强行对应三个业务阶段。

### 4.4 管理入口链

管理端点的额外授权链要求 HOST_SESSION 主体 + admin scope + gateway audience（ManagementAccessRules 在启动时对当前快照校验最低要求，运行期安全链独立执行同一领域规则）；Browser Session 不得成为管理主体。该最低授权检查本身不执行客户端签名验证、Browser Origin 校验与身份维度限流。

本地管理路由在此检查之外，由 LocalPolicyWebFilter 按 `routeId.value()` 识别后复用 IngressAdmissionGlobalFilter，消费准备结果并执行通用接入／准入流程；不能将“最低授权检查不执行配额”理解为“本地管理路由绕过准入”。

## 5. 阶段二：下游治理（语义权威，v1 暂不交付）

只作用于获准请求，输入为两阶段交接物。v1 仅交付其中的 headerApply（安全必需：凭据清除不可延迟），其余能力实现与验收随后续版本交付。

### 5.1 缓存

默认关闭，仅显式主体无关 GET；读取前仍认证及授权。键含缓存策略指纹、EndpointId、method、原始路径/查询和影响响应的 App/Client/语言等维度。主体相关内容不能忽略 Subject 直接共享。

private、no-store、Set-Cookie 响应不缓存；携带 Authorization/Cookie 默认不共享。Vary 必须支持，否则跳过缓存。限制总容量、单响应大小、TTL 和旧缓存窗口；不缓存 SSE/下载流。命中不续期。只有相关策略变化才隔离对应内容。

**v1 保守限定**：

- 仅无请求体、已完成授权且无凭据、无 Subject 的 GET。
- 响应为 200 JSON 或 text/plain。
- 携带条件请求、Range，或响应要求重新验证时跳过缓存。

**缓存键与新鲜度**：

- 缓存键分别对原始请求及受控转发 Header 做摘要隔离；Header 策略变化会隔离旧内容。
- trace/nonce 等变化会降低命中率，但不会漏掉未声明的响应维度。
- 尊重响应 Age/max-age，并将完整读取耗时计入新鲜期。
- 不提供后台刷新、请求合并或跨实例共享缓存。

### 5.2 超时、熔断与降级

区分连接、响应等待、总时限；连接超时必须由 HTTP 客户端实施。流式请求不走普通缓冲响应缓存。响应提交后不能改写标准错误体。

熔断状态实例本地，默认按 routeId 隔离，统计真实下游连接失败、超时、选定 5xx；认证拒绝、网关限流、缓存命中不计入。无关策略变化不得重置状态。熔断不等于并发隔离。

v1 的慢调用时长统计到下游响应 Header，故障状态选定 500/502/503/504；响应 Header 之后的写出失败或客户端取消不污染下游熔断统计。总时限仍覆盖写出，但已提交响应只能终止，不能再降级改写。熔断实例数量有界，容量淘汰后该实例的历史不保留。

只允许标准错误或明确只读端点的有限旧缓存降级；503 表示不可用，调用超时通常 504。不返回伪成功，不自动重试非幂等请求。认证/授权失败不得触发缓存降级。

### 5.3 Header 转发应用

headerApply 执行准备段定稿的计划：发送下游前清除所有受保护字段，再按当前策略写入可信结果；来源缺失则不写入（信任语义见第 6 节）。客户端版本仅清洗后声明，不参与授权。历史名称与其他未注册自定义名称一样处理。

## 6. Header 与信任上下文

**CredentialForwarding**（凭据转发设置值对象）包含 `authorizationHeader`、`cookies`、`allowedCookieNames`，由 `HeaderPolicy` 引用。

- v1 取消端点级默认转发设置，`HeaderPolicy` 是唯一来源，未配置时默认全剥离。
- `ClientAccess.credentials` 仅表示签名凭据，两者不混用。

**HeaderPolicy 构成**：

| 组成 | 取值 |
| --- | --- |
| `trustedHeaders` | `Set<RequestHeader>`，固定输出集合的选择 |
| 普通字段 | 删除 / 受控设置 |
| `authorizationHeader` | STRIP / PRESERVE |
| `cookies` | STRIP_ALL / ALLOW_LIST |

默认删除凭据。构造时校验：HTTP token、名称长度 128、值长度 4096、控制字符、大小写重复、跨操作冲突；允许空字符串值；普通 set/remove 目标不得是已登记的 `RequestHeader` 名称。

**framework-core RequestHeader**：

- 只登记有限的共享自定义 Header（CONTEXT、SIGNATURE），不登记 Host、Content-Length 等标准协议字段。
- 不设 LEGACY 分类或历史名称别名。
- 字符串消费点使用 `headerName()`；`fromName(name)` 验证 HTTP token 后忽略大小写查找，不修剪非法空白；`fromType(HeaderType)` 派生不可变集合。
- gateway `HeaderRules` 独立校验配置名称并规范化，不能将 `fromName` 返回空误判为合法名称。

**trustedHeaders 的固定输出**由 `TrustedHeaderResolver` 统一解析，写入其 `headerName`，禁止重命名映射：

| 输出 Header | 来源 |
| --- | --- |
| APP_ID、APP_PLATFORM | VerifiedApp |
| CLIENT_ID | 已验证的 VerifiedClient |
| USER_ID、SESSION_ID、SUBJECT_TYPE | AuthenticatedSubject |

APP_VERSION 和 SIGNATURE 字段不可选择。framework 不依赖网关上下文。动态更新只改变固定可信字段的选择和普通字段操作，不提供名称映射。

**HeaderRules**（不可变部署规则）：

- 配置名称在构造时规范化为小写存储；非法 token 字符、超长名称及大小写不同的重复名称拒绝。
- 名称准入为部署扩展名单 `extraHeaders` 与 `X-Gateway-` 前缀。
- 已登记的 `RequestHeader`（身份与签名字段）永远不能作为普通 set/remove 目标，部署扩展名单也不能放宽。
- `forbiddenSetHeaders` / `forbiddenRemoveHeaders` 独立否决对应操作。
- 规则不写入业务快照或其内容摘要。
- 当前规则在启动期固定，`HeaderPolicy` 随快照动态更新；不通过异步修改 Properties 放宽运行请求。未来若支持规则刷新须先校验当前策略并整体切换。

**其余约束**：校验名称、重复目标、CR/LF、长度和协议控制字段；未验证 Client 声明不写 CLIENT_ID；不提供历史名称识别、专门清除或兼容转换，历史名称与其他未注册自定义名称一样处理。

## 7. 快照与发布

**入口快照 `IngressSnapshot`** 由 `SnapshotVersion`、`IngressContent`（App/Client/Endpoint 注册表与 CapabilityComposition）、`contentHash` 与 `publishedAt` 构成，不可变。它服务整个入口管道，而非仅 access 阶段，因此使用 Ingress 前缀。

- `contentHash` 计算规范化业务内容，不含版本和 `publishedAt`。
- 一个请求只读取一次快照。

**快照构造即全量校验**：

- Client 引用的 App 必须已登记。
- active 与 grace 凭据都必须在 `publishedAt` 时刻处于有效期内。
- `credentialId` 在全部 Client 间唯一。
- `credentials` 为 null 的 Client 跳过凭据校验。
- 任何校验失败以 `ACCESS_SNAPSHOT_INVALID` 拒绝，配置错误不静默降级。

Bundle、Binding 与 Header 组合由 `CapabilityComposition` 建模，发布前对照同一内容校验绑定引用（`AppId`、已验证 `ClientId` 必须存在）与同层竞争节点冲突。

**Definition 保存与发布**：

- Definition 独立 CAS 保存；Publish 读取全部 revision、组装全量校验、再次确认 revision、CAS 发布。
- 无变化不增加版本。
- 加载先校验再原子切换，失败保留最后有效版本，首次无有效版本则启动失败。
- 未知必需能力拒绝加载。
- 预编译索引只是派生实现，不建第二套运行时版本。

**Client 接入设置与凭据设置**保留独立管理操作，但共用 ClientAccess Definition 和 `sourceRevision`：

- 修改接入设置保留原凭据；更新凭据保留 `ClientStatus`/`allowedApps`。
- 凭据更新要求 Client 已存在。
- 旧 revision 写入冲突，不允许两个操作互相覆盖。
- 共四份 Definition：端点接入状态并入端点注册表定义，不再有独立 ClientCredentialPolicy 仓储。

**发布与回退**沿用固定锁顺序、独立 revision/CAS 正确性边界：

- 回退恢复全部 Definition 后以更大版本发布。
- 永久 rollbackState 阻断普通保存/发布，支持幂等重入，全部完成才清除。
- 不新增领域事件/补偿调度。

**回退顺序（rollback → publish）**：

- 先验证目标存在、HeaderRules 和完整发布有效性，再于首次恢复写入前激活阻断。
- 发布前取一次业务时间校验凭据有效期；历史凭据已过期则在写入前拒绝。
- 前置失败不新增阻断，也不清除上次恢复遗留的阻断。
- 每份成功恢复的 Definition CAS 都是重试检查点；部分失败保持阻断。
- 目标内容已发布而清除失败时，重试沿用原发布时间校验，不重复增加版本或修改发布时间。
- 支持更换合法目标重试。

快照接纳由 `IngressSnapshot.acceptanceOf` 统一判定：低版本 STALE 忽略；同版本同摘要 UNCHANGED 幂等接纳；同版本不同摘要 CONFLICT 拒绝；已通过全量校验的高版本 ADVANCE 接纳。Nacos `sourceRevision` 只服务技术 CAS，不替代 `snapshotVersion`。通知、原子替换及 LKG 持久化由适配器负责。

发布与回退统一返回 `IngressSnapshotUpdateOutput(snapshotVersion, contentHash, publishedAt)`，表示操作完成后的快照事实，不暗示只执行 publish，也不代表全部实例已切换。publishedAt 仍是该快照的发布时间；幂等回退复用原快照时不改时间。

**编排职责**：

- `AccessSnapshotCommandService` 仅委派管理入口。
- `AccessPublicationCommandService` 与 `AccessRollbackCommandService` 分别编排发布和恢复，共用类型明确的 `AccessDefinitions` 与单一 `AccessSnapshotPublication` CAS 边界。
- 保存用例通过 `AccessDefinitionWriter` 组合复用阻断／revision／CAS 校验，各命令独立表达业务更新，不使用继承式 CRUD 模板。

不同实例允许短暂相邻版本；不承诺全局瞬时撤销。熔断、缓存、令牌余额是执行状态，不作为快照数据；无关发布不重置状态。

## 8. 管理面接口

管理面分为 definition 与 snapshot 两组、各分 command 与 query 共四个门面（`api/facade/admin/{definition,snapshot}`）：命令门面仅在存在可写 Definition 存储时装配，查询门面在只读部署同样可用。运行时请求路径不经过管理门面。

**两个版本轴独立前进**：

| 轴 | 粒度 | 性质 |
| --- | --- | --- |
| `sourceRevision` | Definition 文件级令牌——每份集合文件（App、Client、Endpoint）各一个 | 由内容摘要派生、不透明、随整份文件前进，不随单条目孤立存在 |
| `snapshotVersion` | 全局发布版本 | 每次发布前进 |

两个版本轴不能相减或比较大小；“已保存未发布”应比较当前 Definition 组装内容与发布内容的规范化摘要。CAS 粒度是文件，命令粒度是意图。读取时由 `RevisionedAccessDefinition`（值 + 文件级 revision）与 `RevisionedAccessSnapshot`（快照 + 源 revision）成对携带，后续 CAS 不能拿值配另一份令牌。

**Definition 查询**：`getApps`/`getClients`/`getEndpoints` 按集合返回全量内容与该集合共享的 sourceRevision；revision 为 null 表示该 Definition 尚未初始化。查询不回显 secretRef，凭据只回显 credentialId、协议与有效期供编辑参考。

**Definition 命令**（全部返回保存后的新 sourceRevision，供连续编辑免重查）：

- `saveApp`/`saveClient`/`saveEndpoint`：单条内容 upsert，不含 status。创建以 ENABLED 初始化，已有实体的 status 不被 save 改变——发布才是生效闸门，"预置配置、暂不发布"即预置工作流。saveClient 只动接入设置、绝不触碰凭据；saveClientCredential 整对轮换（grace 缺省即清除），要求 Client 已存在，凭据不随接入编辑往返。
- `setAppStatus`/`setClientStatus`/`setEndpointStatus`：唯一的上线/下线入口，载荷仅 id + status，与内容编辑分属不同授权与审计事件。
- 删除不提供：下线一律走 status；物理清理与批量迁移走 Nacos 侧整体重建 Definition（绕过 API，令牌随之变化，之后经发布生效）。
- `expectedSourceRevision`：不透明 CAS 令牌，null/空 = 断言该 Definition 尚不存在（创建）；否则必须与最近一次读取或保存返回的值一致。并发保存不加写锁：存储层 CAS 裁决，失败方重读重放，冲突可见优于互相排队。同一文件的编辑互相冲突——Client 的接入设置与凭据轮换共享文件因此互相覆盖受拒；不同集合互不影响。

**Snapshot 门面**：`release` 显式发布，不携带调用方令牌（发布内部读取全部 Definition、组装校验、再次确认 revision 后 CAS 发布）；`rollback` 回退到正数的历史版本；`get` 返回当前生效快照，从未发布时字段为 null。发布与回退的锁顺序与阻断语义见第 7 节。

## 9. 分层与模块职责

domain 持有模型与纯规则；application 编排用例与端口；infrastructure 实现安全/存储/缓存/配额；interfaces 处理 HTTP 生命周期；boot 装配。不建设动态插件或脚本平台。

包边界：`application.configuration.{definition,editing,publication}` 管理编辑态与发布；`application.ingress` 编排运行期三段；`domain.ingress.access` 持有访问决策和身份事实；`domain.ingress.snapshot` 持有完整快照与版本接纳规则。配置管理不再使用含糊的 application.access 包，快照不再混入访问规则包。

**阶段一服务对应**：

| 服务 | 职责 |
| --- | --- |
| `PreparationStageCommandService` | 准备段：捕获单次快照、技术匹配候选，构造 PRE_AUTH 上下文并调用统一能力服务 |
| `AccessStageCommandService` | 接入段唯一入口 `evaluate(command, subjectAuth)`：客户端验证、主体认证 join、接入决策 |
| `AdmissionStageCommandService` | 准入段：仅消费 Allowed，构造 POST_AUTH 上下文，调用统一能力服务并交接其产出 |
| `CapabilityExecutionCommandService` | 统一能力用例：接收 CapabilityExecutionCommand，编排解析→规划→执行，返回 CapabilityExecutionOutput |
| `IngressAdmissionCommandService` | 组合入口：依序调用接入段与准入段，处理拒绝短路及跨阶段取消／异常 |

命名与交接：三个服务都带 `Stage`，命令与输出同构命名。输入由上一段的成功产出包裹而成：`AccessStageCommand(Prepared, signatureEvidence)`、`AdmissionStageCommand(Allowed)`，拒绝不进管道。Allowed 保留 Prepared 及其通用执行记录，见 4.3。

`IngressAdmissionGlobalFilter` 必须消费准备阶段已捕获的快照、端点、配额记录和完成标记，不自行重读、补选端点或补空执行记录；接口层负责证据、实际 SCG 路由核对、异步桥接、Header 写入和响应；`GatewayRequestAttributes` 属于 interfaces。

**阶段二服务（v1 暂缓）**：

- `DownstreamGovernanceRules`：根据独立请求／响应事实决定缓存读取、捕获、存储、STALE_CACHE 降级和下游故障观察，解释受管理 streaming 标签。
- `DownstreamGovernanceCoordinator`：先查缓存，再申请熔断许可，返回命中／执行／降级结果。
- `DownstreamExecution`：管理单次执行，许可最多结算一次。
- `DownstreamResponseLifecycle`：负责 HTTP 超时、缓冲、取消及连接释放。
- `GatewayResponseWriter`：负责提交前恢复响应元数据。
- 缓存适配器：保留控制头解析、摘要键、容量及条目存储，复用领域资格规则而不重新定义它们。

### 9.1 对象转换与 Mapper 边界

管理 API 的命令输出与查询视图区分命名：发布／回退返回 `SnapshotUpdateApiOutput`，当前快照查询返回 `SnapshotApiView`；均保留 snapshotVersion、contentHash、publishedAt，不改变 HTTP 或快照存储协议。application 的 `IngressSnapshotUpdateOutput` 不变。公共嵌套命令载荷与查询条目分别使用 `*ApiCommand`、`*ApiView`，仅改变 Java 类型名称，不改变 JSON 属性和嵌套结构。受影响 record 的集合构造时防御性复制，保留 null，不擅自归一为空集合。


结构映射与语义行为必须分别识别，不能仅因类名含 Mapper，或代码使用构造函数，就视为满足转换规范。

| 转换边界 | 所属层及约束 |
| --- | --- |
| 管理 HTTP Request → API Command | interfaces.rest 的独立 mapper 契约；结构字段与嵌套集合由 MapStruct 实现。控制器只处理协议、调用 Facade。 |
| 管理 API Command → application Command；领域读取对象／应用产出 → API View/Response | interfaces.facade 的独立 mapper 契约；字段、枚举及集合转换收口，Facade 不散落复制代码。凭据查询投影仅暴露编辑所需字段，不回显 secretRef。 |
| 本地 Properties → Stored 文档 | infrastructure.policy 的独立 mapper 契约；Properties 只承载配置绑定和约束，不内嵌 toStored 字段复制。 |
| 领域对象 → Stored 文档 | infrastructure.policy 的结构映射；复用能力组合及子对象转换，不重复复制同一字段组。 |
| Stored 文档 → 领域对象 | 显式语义重建，经聚合、值对象及注册表的构造／工厂执行不变量；MapStruct 可调用这些方法，不得通过 setter 绕过校验。 |
| 领域内容 → Canonical 内容 | 规范化编码边界：排序、字段格式、空值省略及时间编码保持显式语义；其中可分离的结构复制复用 mapper。重构不顺带改变规范化协议或重算示例摘要。 |

契约与 MapStruct 实现分开：契约按转换职责命名；实现类置于契约包的 mapstruct 子包，使用 `*MapStructMapper`，实现契约并声明 `@Override`。共享配置在所属模块内复用，保持 Spring component model 与未映射目标字段报错；局部投影显式声明忽略字段或白名单，不降低全局约束。不同外层模块不为复用配置而引入反向依赖。

生产链通过容器注入 mapper 契约，MapStruct 子映射通过 `uses` 复用；不在业务对象或 mapper 内自行获取另一份非托管 mapper，也不以匿名实现承载整套手写结构复制。自动配置与组件扫描必须保证同一契约的装配一致。

当前实现：HTTP 使用 `GatewayPolicyAdminRestMapper`，定义及快照 Facade 分别使用 `AccessDefinitionApiMapper`、`AccessSnapshotApiMapper`；App、Client、Endpoint 和能力组合的 Properties 各有独立转换契约。`DefaultStoredAccessSnapshotMapper` 保留领域重建、路径校验及注册表排序，字段投影委托 `AccessSnapshotDocumentMapper`，后者通过 `uses` 复用 `CapabilityCompositionStorageMapper`。`DefaultCanonicalPolicyContentMapper` 保留规范化编码并注入能力组合 mapper，不自行构造存储 mapper。interfaces 与 infrastructure 分别复用模块内的严格配置，自动配置仅扫描各自 mapper 实现包。

领域构造、状态转换、授权决策、签名编码及用例结果的语义组合继续使用显式方法。特别是新建聚合的状态、修改时保留的凭据、Header 默认与可信身份选择，不能由自动映射替代其领域／用例规则。`CapabilityExecutionOutput` 组合本次解析结果和实际执行记录；阶段交接仍保留同一份快照与事实，不为了引入 mapper 而重新解析、复制另一套上下文或增加纯转发层。

## 10. 错误及破坏式更新

EDGE 前缀 800：000–099 共享领域；100–149 快照；150–199 端点注册；200–249 App；250–299 Client；300–599 未分配领域；600–699 应用；700–799 自有技术；800–899 外部适配；900–999 保留。已分配编号与语义不复用。

开发阶段允许直接更名 Java/API/配置及重建开发快照，不保留旧 schema 别名层；历史签名算法保留。
