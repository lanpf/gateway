# gateway 领域设计 · v1

本文是 v1 技术方案的领域设计权威来源。职责与交付见 [RESPONSIBILITIES.md](RESPONSIBILITIES.md)，运行配置见 [README.md](../README.md)。

一次请求的生命周期分为两个宏观阶段：**阶段一入口准入**与**阶段二下游治理**。阶段一完成身份验证、接入评估与限流；阶段二实施已获准请求的超时预算与 Header 转发应用。

## 1. 请求生命周期：两阶段与能力库

### 1.1 阶段一：入口准入（v1 交付）

决定放不放行、以什么身份放行、扣多少额度，全部发生在代理之前。三段固定管道：

1. **准备段（prepare）**：捕获单次快照与原始请求事实，取得技术路由候选，执行认证前 IP 限流。此段不依赖任何身份——未知路径与歧义候选同样受保护。
2. **接入段（access）**：主体认证与客户端签名验证协作，端点/App/Client/Subject 接入评估，产生接入决策与获准上下文。
3. **准入段（admit）**：使用获准上下文解析能力绑定，产生 EffectiveCapabilityPolicies，执行认证后限流，定稿 Header 准备（凭据清除计划与可信 Header 取值）。

### 1.2 阶段二：下游治理

只作用于获准请求：实施连接、响应等待与总时限，应用 Header 转发计划。被拒绝的请求不产生任何下游动作。

**交接物**：入口准入向下游治理移交获准上下文（AccessGrantedContext）、EffectiveCapabilityPolicies（限流、超时、Header 有效配置）、配额执行记录、Header 准备结果与捕获的原始证据。转发前保留捕获的原始请求证据；Header 安全清除属转发必需项。

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
| capabilityBinding 解析 | 准备段 / 准入段，统一能力服务按当前可信事实解析 | 一 | 是 |
| rateLimit（post-auth 槽位，身份维度） | 准入段 | 一 | 是（EXACT） |
| headerPreparation | 准入段 | 一 | 是 |
| **timeout** | 下游治理（`FORWARD` 相） | 二 | **是** |
| **headerApply（凭据清除 + 可信写入）** | 下游治理（`FORWARD` 相） | 二 | **是（安全必需）** |

当前可配置能力为 `rateLimit`、`timeout`、`headerApply`。限流在阶段一的 PRE_AUTH / POST_AUTH 槽位执行；超时与 Header 应用在阶段二的 FORWARD 槽位执行。

限流是一个能力、两个槽位：IP 规则最早可在 pre-auth 执行；身份维度必须等待 post-auth。同一规则跨阶段最多执行一次，支持两个槽位不代表每条规则执行两次。 执行拓扑固定、不可由调用者指定，原因有三：

- **顺序编码安全不变量**，违反时故障静默：Header 写入先于认证即透传伪造身份；IP 限流后置即失去未知路径保护。
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

App、Client、Endpoint 是不可变普通类聚合，以 Lombok Getter 读取字段，equals/hashCode 仅比较各自唯一 ID。内容编辑保留原状态，Client 接入编辑还保留凭据；创建默认 ENABLED，状态切换和凭据轮换由聚合方法执行。application 负责读取、锁、revision 与 CAS，不再自行决定这些状态保留规则。Java 调用方使用 getId 等生成 Getter；协议中的 appId/clientId/endpointId 由映射契约保持。

配置比较须使用 sameDefinition；身份相等不表示配置一致。EndpointMatch 折叠身份和全部配置都相同的候选，同 ID 的不同匹配器、状态或接入配置仍为 Ambiguous；回退同样按配置决定是否跳过已恢复的 Definition。

标识来源由以下契约约束，不引入数值 ID 生成器：

| 标识 | 来源及维护方 |
| --- | --- |
| AppId、ClientId、EndpointId | gateway 受管理 Definition，由配置维护方提供稳定字符串 |
| CredentialId | Client 凭据配置，由配置维护方提供并在轮换时保持两份 ID 不同 |
| CapabilityBundleId、CapabilityBindingId、RateLimitRuleId | 策略配置维护方提供；配额规则 ID 跨快照更新保持稳定 |
| CapabilityId | 代码目录登记；配置只能选择已部署的精确名称 |
| ServiceId、RouteId | 服务发现与 SCG 路由的部署标识，配置引用其稳定名称 |
| SubjectId、SessionId | 经过认证的 Token 或 user-auth 验证响应恢复；gateway 不自行生成 |

实体字符串标识继承 AbstractStringEntityId；AbstractLongEntityId 仅保留既有抽象契约，不为当前字符串模型引入发号依赖。

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
| SubjectId / SessionId | 主体标识与会话标识值对象。 |
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
| AccessDenyReason | 十六种拒绝原因；禁用与不存在共用 NOT_FOUND 。 |
| AccessGrantedContext | 仅含 app（VerifiedApp，必填）、client（VerifiedClient，可空）、subject（AuthenticatedSubject，可空）。 |

### 2.5 快照与版本

| 模型 | 定义 |
| --- | --- |
| Revision | Definition 文件级源修订令牌，非空白字符串包装；不透明，只用于 CAS 比较。 |
| SnapshotVersion | 全局发布版本，正数 long 包装；0 非法。 |
| IngressContent | 快照内容：App / Client / Endpoint 三个注册表与 CapabilityPolicyConfig。 |
| IngressSnapshot | 唯一版本化的不可变入口快照：version、content、contentHash、publishedAt。 |

### 2.6 能力配置与运行模型

| 模型 | 定义 |
| --- | --- |
| CapabilityPolicyConfig | 同一快照内 Bundle 与 Binding 的完整配置；普通类保存两份不可变 Map 索引，唯一公共构造器接收 Bundle 与 Binding 集合并按条目自身身份建索引、判重并校验 Binding 引用的 Bundle 存在；跨注册表引用与同层竞争校验收口在 CapabilityPolicyConfigValidator.validate(policyConfig, apps, clients, endpoints)。 |
| CapabilityBundle / CapabilityBundleId | 开放的不可变能力配置集合；普通类持有按 CapabilityId 的不可变索引，唯一公共构造器接收 Bundle 标识与配置条目集合，按条目自身身份建索引并判重，每种能力零或一份。 |
| CapabilityBinding / CapabilityBindingId | 按条件（serviceId/endpointId/tags + appId/verifiedClientId/subjectType）把 Bundle 绑定到端点。 |
| EffectiveCapabilityPolicies | 当前可信事实下的生效策略容器；通过 get(policyType) 类型化读取各自的 Effective*Policy，不持有具体能力字段，不携带执行状态或槽位。 |
| CapabilityConfig / CapabilityId | 单项不可变能力配置契约与稳定能力标识；配置类型与标识由注册定义校验。 |
| CapabilityConfigRule<C> | 能力专属配置规则：声明 id()/configType()，validate(List<C>) 接收同一能力的完整配置列表，不感知 PolicyConfig、Bundle、Binding 或 Composer。 |
| CapabilityPolicyConfigValidator | 校验整份能力策略配置的 ID／类型、跨配置规则、注册表引用与同层竞争；持有 ConfigRule 和固定注册元数据，不依赖 Assembler。 |
| CapabilityPolicyComposer<C, P> | 仅声明 id、configType、policyType 与 compose；将有序 Config 组合为对应 Effective*Policy，不承担全量校验或同层冲突判断。 |
| CapabilityPlanningEntry<P> | 请求级规划输入：PlannableCapability<P> + P policy；仅持有本次非空生效策略，不持有配置来源、快照或读取回调。 |
| CapabilityPolicyAssembler | 持有不可变 Composer 目录，委托 Validator 校验，再按可信事实匹配并排序 Bundle，组合 EffectiveCapabilityPolicies；不保存请求状态或校验规则。 |
| CapabilityContext / CapabilityFact | 当前槽位、请求事实、可空端点及获准上下文；事实可用性由上下文推导，区分尚未认证与接入完成但无主体。 |
| PlannableCapability | **唯一的规划契约**：声明支持槽位、所需事实、重复策略，并产出强类型执行单元。 |
| CapabilityUnit | 能力产出的强类型执行单元；单元可额外声明事实依赖，由 planner 逐单元校验。 |
| CapabilityPlanner / CapabilityPlan | 无状态规划器接收本次请求的 Entry 列表、Context 和已执行键，校验槽位／事实并去重；不引用 EffectiveCapabilityPolicies，也不保存请求配置。 |
| CapabilityExecution / CapabilityExecutionKey | 累计执行键与当前槽位强类型结果；键为 `CapabilityId` 能力标识 + 单元标识，按槽位重复时额外包含槽位。 |
| TimeoutConfig / HeaderConfig | 单值完整配置节点，随 Bundle 整体覆盖，无独立身份、无逐请求决策。 |
| RateLimitConfig | Bundle 的限流配置节点：规则组 + `RedisFailureMode`；节点内多条 Rule 各自可寻址执行。 |
| RateLimitRule / RateLimitRuleId | 限流的多值可寻址规则：Rule 有独立身份构成配额键、按维度落到 pre-auth/post-auth 槽位。 |

**能力的维度在 `PlannableCapability` 的四个方法上直接表达**：

| 维度 | 在哪里表达 |
| --- | --- |
| 配置怎么合并 | 组合解析规则（§3：单值覆盖、限流累加） |
| 在哪个阶段执行 | `slots()` |
| 需要哪些事实 | `requiredFacts()`（能力级与单元级各一份） |
| 产出哪些单元、单元怎么标识 | `units()` 与 `unitId()` |
| 能否重复执行 | `repeat()` |

**所有能力都进 planner**：`CapabilityPlanner` 接收固定顺序的 `CapabilityPlanningEntry<?>`，每项绑定一个 `PlannableCapability<P>` 与本次非空生效策略 P。只有一个单元、没有任何可选项的能力同样走这条路径——它需要完全相同的执行机制（上下文、事实、单元、结局、去重键），唯一区别是「没有可选项」。为它另开一条执行路径要多一份契约和一个消费者，而 planner 已经够用。

**模型的当前形态**：三个固定执行点 PRE_AUTH、POST_AUTH、FORWARD；四个可用输入事实 ENDPOINT、ACCESS_GRANTED、VERIFIED_CLIENT、AUTHENTICATED_SUBJECT。完整约束见 §5.3。

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
- v1 聚焦入口访问控制，凭据转发设置不再属于端点，由 `HeaderConfig` 承载。

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

## 3. 能力组合与绑定解析

**组合条目统一按身份索引**：CapabilityPolicyConfig 是普通类，保存 Map<CapabilityBundleId, CapabilityBundle> 和 Map<CapabilityBindingId, CapabilityBinding> 两份不可变索引，通过 getBundles() / getBindings() 读取。唯一公共构造器接收 Collection<CapabilityBundle> 与 Collection<CapabilityBinding>，直接按条目自身身份建立索引：元素非空、重复 ID 即使内容相同也拒绝（不静默折叠或覆盖）、null 集合归一为空集合，Binding 引用的 Bundle 必须存在于同一组合；索引经不可变复制并与输入容器隔离。存储／协议边界的来源 key 与还原对象 ID 的一致性由各自的重建转换核对，不属于领域构造器。

组合相等性与 hashCode 基于两份索引的全部内容，不依赖输入顺序；它不是按单一 ID 比较的聚合根。匹配遍历 Binding 索引的 values，再按 specificity 与 bindingId 显式排序，不以 Map 迭代顺序决定优先级。HTTP／存储统一使用 bundles 与 bindings 两个对象，分别按字符串 ID 索引，条目内保留 bundleId／bindingId。外层语义转换在标识规范化时拒绝重复键，并核对还原对象的 ID 与来源 key 一致。原始 JSON／YAML 的重复 key 在解析阶段拒绝，不能等覆盖后再判重。存储两份 Map 均按 key 稳定排序；bindings 从数组变为对象属于破坏式 schema 更新，规范化摘要随之改变。

**两项职责，不增加中间层**：CapabilityPolicyAssembler.assemble(policyConfig, endpoint, granted) 匹配 Binding、按优先级选择并去重 Bundle、提取每种能力的配置，直接调用对应 CapabilityPolicyComposer.compose(List<C>)，最后组装 EffectiveCapabilityPolicies。该容器是本次各能力生效策略的集合，不是执行计划；是否执行仍由 Planner 决定。

不增加注册层。CapabilityPolicyAssembler 接收 Composer 集合与现成的 CapabilityPolicyConfigValidator，保留目录、匹配与组合职责，在 assemble 前调用内容校验，不负责创建 Validator，也不对外转发 validate。保存、发布和编解码直接依赖同一个 Validator，请求链通过 Assembler 使用它。装配处通过 CapabilityPolicyConfigValidator.forComposers 从同一份 Composer 集合提取能力 ID 到配置 Class 的不可变索引，并传入 ConfigRule 列表与累加能力集合；Validator 不持有或调用 Composer，不依赖 Assembler。Assembler 构造时拒绝与 Validator 目录的能力 ID 集合或配置类型不一致；协议目录也按能力 ID 与配置类型检查完整性，无需通过 Assembler 查询 Composer 对象。每个固定规则 Validator 仅以弱引用记忆最近一次成功校验的 PolicyConfig 对象身份，不比较内容相等性、不计算内容哈希、不使用共享锁；同内容的新实例仍需校验，实例被回收或被另一成功实例替换后再次校验。并发未命中允许重复执行纯校验；失败不缓存，引用校验不缓存且每次执行。Composer 只提供 id()/configType()/policyType()/compose(List<C>)，不感知 Bundle、请求或槽位，不替代完整配置校验。

累加能力集合必须是已注册能力标识的子集，非累加能力在同层存在不同配置来源时拒绝竞争。Validator 先校验完整组合的能力 ID 与配置类型，再按能力提取全部配置（包含未绑定、未命中 Bundle，不去重相同配置），调用 CapabilityConfigRule<C>.validate(List<C>)。ConfigRule 必须声明匹配注册目录的 id()/configType()，每个能力最多一项 ConfigRule，无额外规则的能力无需空实现。列表非 null、不可变且无 null 元素，不承载优先级；没有配置时传空列表。HeaderConfigRule 逐份检查部署限制，RateLimitConfigRule 在全体限流配置之间校验 RuleId 唯一及 EXACT 约束。具体 ConfigRule 不感知 PolicyConfig、Bundle、Binding 或 Composer。生效策略不重复执行配置规则。

新增能力提供自己的 Config、EffectivePolicy、PolicyComposer 和运行能力／执行器，并登记必要的组合 ConfigRule 与协议编解码；不修改 Bundle、Assembler、Planner 或有效策略容器。运行目录、配置目录与协议目录的完整性校验继续保留，不增加动态插件发现或单／多槽位继承体系。

Config 构造器继续保护自身不变量（包括单份限流配置内 RuleId 判重），不接收整个 PolicyConfig 或读取部署 Properties。跨配置和部署准入规则由各自的 *ConfigRule 承担；Composer 仍只负责有序配置组合。保存与发布仅调用统一领域校验，编解码在语义重建／编码边界直接调用共享 Validator，不再维护 Header 专属遍历或经 Assembler 转发校验。绑定引用、范围及同层竞争规则由 CapabilityPolicyConfigValidator.validate(policyConfig, apps, clients, endpoints) 的全量校验承担，PolicyConfig 不再依赖服务。

本组合是数据层组合机制，服务准备段、准入段与转发前的能力节点解析；**三个执行点由 `CapabilityExecutionCommandService` 统一编排**。能力节点既参与发布校验也参与执行。

**Config 与 Rule 的分界**：

| 层级 | 语义 | 身份 | 合并方式 |
| --- | --- | --- | --- |
| `*Config`（RateLimit / Timeout / Header） | Bundle 的能力配置节点；限流为规则组节点，其余为单值节点 | 每种能力在 Bundle 内恰好零或一份 | 单值节点被 Binding 整体覆盖；限流规则组累加、同 Bundle 去重 |
| `RateLimitRule` | 可独立执行的可寻址单元 | 有独立身份，构成配额键 | 可累加、跨阶段去重 |

**单值 Config 被覆盖，限流 Config 累加，RateLimitRule 被执行**——限流规则组累加而非覆盖，这一差异决定了两个能力族的合并语义不同（见第 4.1 节）。

**命名约定**：治理能力的组合与解析使用 `Capability*` 词族，解析结果使用 `EffectiveCapabilityPolicies`。类型后缀在全领域统一为固定家族，**同一种后缀必须是同一种含义**：

| 后缀 / 前缀 | 含义 | 成员 |
| --- | --- | --- |
| 前缀 `Capability*` | **配置、组合与执行机制**——策略配置、绑定、解析、规划及执行记录 | `CapabilityPolicyConfig`、`CapabilityBundle`、`CapabilityBinding`、`CapabilityPolicyAssembler`；`capability.runtime` 下的槽位、规划与记录模型 |
| `EffectiveCapabilityPolicies` | **解析结果**——当前请求适用的能力配置 | 累加后的限流规则组，以及覆盖解析后的超时、Header 节点 |
| 后缀 `*Config` | **快照内配置**——整份能力策略配置，或 Bundle 中登记的单项能力配置节点，随快照发布 | `CapabilityPolicyConfig`、`RateLimitConfig`、`TimeoutConfig`、`HeaderConfig` |
| 后缀 `*Rule` / `*RuleId` | **单条规则及其标识**（有独立身份，构成配额键） | `RateLimitRule`、`RateLimitRuleId` |
| 后缀 `*Quota` | **配额值对象**——选定的规则、维度取值与故障方式，不执行；执行端口用角色命名（application 的 `QuotaAcquirer`） | `ApplicableQuota` |
| 后缀 `*Mode` | **行为选择枚举** | `RedisFailureMode`、`RateLimitMode`、`ClientVerificationMode`、`CookieForwardingMode`、`AuthorizationHeaderForwardingMode` |
| 后缀 `*ConfigRule` | **能力配置判定器**——接收同一能力的完整配置列表，校验跨配置或部署准入约束，不属于快照内容 | `CapabilityConfigRule`、`HeaderConfigRule`、`RateLimitConfigRule` |
| 后缀 `*Rules` | **非快照内容的规则判定器**——静态谓词或携带少量参数，职责是判定/校验，不随快照发布 | `SubjectAccessRules`、`SignatureAuthenticationRules`、`ManagementAccessRules` |
| 后缀 `*Evaluator` | **决策引擎**——消费多方证据，产出 Decision 对象 | `AccessEvaluator`、`RateLimitEvaluator` |

与治理无关的同名标识**不属于本词族**，读到时按各自领域理解：`LocalPolicyWebFilter` / `PolicyAwareBearerTokenConverter` 中的 Policy 指"本次请求的准入策略"。

**配置统一使用 `*Config` 后缀**：CapabilityPolicyConfig 表达整份策略配置，包含 Bundle 定义与 Binding 绑定；CapabilityConfig 契约仅用于 Bundle 内的单项能力节点，CapabilityPolicyConfig 不实现它。Bundle 保存原始配置，解析器输出对应的 `Effective*Policy`。Header 与 Timeout 整体覆盖；限流累加各命中 Bundle 的配置，每份 RateLimitConfig 保留规则组与 RedisFailureMode，不能扁平化后丢失各组的故障处理方式。

**Bundle 不封闭枚举能力**。各配置实现 CapabilityConfig，按 CapabilityId 注册；当前内建 RateLimitConfig、TimeoutConfig、HeaderConfig。新增能力不修改 Bundle、Binding、有效配置容器或通用 Assembler／Planner，而是新增策略组合器、强类型执行契约与外层编解码注册。执行点和安全顺序仍由代码固定，不支持动态下载或配置 Java 类名。

**策略组合器与执行定义分离**：CapabilityPolicyComposer<C, P> 将有序 Config 列表组合为生效策略 P，PlannableCapability<P> 消费对应策略，二者由 application 注册项连接。

| 原始配置 | 生效策略 | 未命中配置 |
| --- | --- | --- |
| HeaderConfig | EffectiveHeaderPolicy(HeaderConfig config) | 安全默认配置（全部可信字段 + 全剥离凭据），config 必填 |
| RateLimitConfig | EffectiveRateLimitPolicy(List<RateLimitConfig> configs) | 不可变空集合；元素非空，各组保留故障模式 |
| TimeoutConfig | EffectiveTimeoutPolicy(TimeoutConfig config) | 非空策略内 config 为 null，configured() 为 false，不生成单元 |

生效策略均非空，不持久化、不参与快照摘要。超时 Unit／Outcome 必须携带非空 config，不能将未配置状态作为执行预算。

**类型化访问边界**：EffectiveCapabilityPolicies 保存声明的 Class<P> 与生效策略 P，按策略类型索引，通过 get(EffectiveHeaderPolicy.class) 等方式读取，不持有或依赖 Composer。按精确声明类型查询，不扫描继承关系；缺失类型拒绝。Assembler 注册时拒绝重复能力 ID 和重复策略类型，组装时拒绝 null 或与 policyType() 不符的产出，容器也拒绝重复类型。每种能力拥有独立生效策略类型；不增加 CapabilityPolicy 标记接口、Key 或 Registry。配置标识与类型检查由根包 CapabilityConfig.requireType(id, configType, config) 承担，Bundle.find(id, configType) 与协议编解码复用它；Bundle 不依赖 Composer 或 Assembler，缺失节点返回空，已存在但类型不匹配的节点拒绝。Composer 无状态，类型元数据固定，配置与生效策略不可变。

内置能力 ID 由 BuiltInCapabilityCatalog 的 HEADER_APPLY、RATE_LIMIT、TIMEOUT 统一声明，配置与运行能力不再依赖具体 Composer 获取身份。CapabilityId 仍为开放值对象，扩展能力自行声明稳定 ID。CapabilityConfig、CapabilityConfigRule、CapabilityPolicyComposer 与 PlannableCapability 分别声明 CapabilityId id()，不继承共享身份父接口；四个契约各自表达配置归属、校验对象、组合对象和规划能力，通过注册检查保证标识一致。HTTP／存储中的能力名称不变。

`CapabilityBinding` 通过 `bundleId` 引用同一快照内的 `CapabilityBundle`。编辑 Bundle 不影响运行请求，显式发布后影响全部引用它的 Binding；运行时不查询最新编辑态。

**绑定优先级**：全局 < 服务 < 标签 < 端点。

- 可附加 `AppId`、已验证 `ClientId`、`SubjectType` 条件；条件采用 AND，标签集合全部满足。
- 上下文条件不额外提高优先级。
- 没有任意脚本。

**发布校验**：

- 校验引用、范围与受影响端点；通过 Assembler 拒绝未知能力、配置类型不匹配及能力专属的跨 Bundle 约束。
- 同层可同时命中且竞争同一节点的**不同 Bundle** 拒绝发布；**同一 Bundle 被多条绑定重复命中不算冲突**——两次引用的是同一配置来源，无论哪条胜出，请求解析到的节点完全相同，因此按幂等处理。这也与限流「同一 Bundle 只执行一次」的方向一致。
- 限流累加而非覆盖，同一 Bundle 重复引用只执行一次限流。重复 Bundle 在解析列表中保留最具体的命中位置，确保其单值节点仍可覆盖较宽泛配置。
- 限流规则标识全局唯一等规则由 RateLimitConfigRule 校验，通过默认目录登记到全量校验路径；不写死于通用 PolicyConfig 或 Assembler。

**省略能力键**表示不声明该能力；显式 null 节点非法，不能用空值关闭继承能力。需要例外时应收窄上层绑定范围或调整受管理标签。

**这是产品约束，不是实现偶然**：v1 支持「继承」与「整体覆盖」，**不支持局部关闭继承来的能力**。解析后的 EffectiveTimeoutPolicy 始终非空，其 config 可为 null，表示没有任何命中配置；这不等于允许 Bundle 提交 null 来关闭全局 timeout。Header 的安全清除属转发必需项（§5.2），不参与「是否配置了该能力」的判断。

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
   - 入口：准备段只构造 `CapabilityExecutionCommand(policyConfig, context, executed)` 并调用 `CapabilityExecutionCommandService.execute(command)`；配置来自已捕获快照，首次执行键为空。以下解析、规划与执行均由该应用服务统一编排，阶段不分别持有 Assembler/Planner。
   - 解析：`CapabilityPolicyAssembler.assemble(policyConfig, endpoint, granted)` 只使用当前可信事实；认证前不使用声明 App/Client 作为绑定条件。null 端点仅匹配全局无身份绑定。
   - 规划：`CapabilityPlanner` 检查能力声明的槽位与所需事实，再选择执行单元；`RateLimitCapability` 只产生可用 IP 规则的强类型单元。
   - 执行：按固定注册顺序执行批次；能力内部聚合，能力之间遇拒绝短路，取消沿协作链传播。统一返回 `CapabilityExecutionOutput(capabilities, execution)`，包含本次解析结果及执行记录；拒绝同样保留解析结果，异常不伪造允许输出。

**通用执行契约**：`CapabilityRepeat.ONCE_PER_REQUEST` 的键为能力标识 + 单元标识；`ONCE_PER_SLOT` 额外包含槽位。不同能力可复用单元名称，互不干扰。仅完成的批次写入执行记录（拒绝也是完成）；不记录缺失事实、未匹配、取消或未执行单元。规则不得通过配置调整安全执行顺序。新增能力需提供领域定义、强类型单元与结果、应用执行器及必要的 HTTP 结果投影，在装配处固定登记，不修改准备／准入编排。当前登记限流、超时与 Header 应用：限流使用 PRE_AUTH / POST_AUTH，超时与 Header 应用使用 FORWARD。

**限流（两槽位共享语义）**：

| 事项 | v1 语义 |
| --- | --- |
| 交付模式 | 只交付 EXACT；PREALLOCATED 为后续模式，不允许启用或静默替换 |
| 扣减 | Redis 原子令牌桶，每次扣减共享额度 |
| 多规则 | 多条适用规则全部执行，相同维度不能只执行第一条 |
| 槽位覆盖 | 认证前 IP 保护覆盖未知路径；认证后执行身份维度规则 |
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
- 规则身份为值对象 `RateLimitRuleId`（与 AppId/ClientId 同款），能力标识为 `CapabilityId`，执行键不再使用裸 String。
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

签名请求及 GET 请求的入口 body 证据采集上限为 1 MiB，超限返回 413；普通未签名非 GET 请求不由该证据采集器统一缓冲。

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

准入段只有 `AdmissionStageCommandService.admit(AdmissionStageCommand)` 一个入口，输入只来自接入段的成功产出；不依赖 AccessStageCommandService，也不重新认证。它构造 POST_AUTH 的 CapabilityContext，并将同一快照的 policyConfig 及 Prepared 的执行键交给 CapabilityExecutionCommandService；阶段不再分别调用解析器与规划器。

- **能力绑定解析（capabilityBinding）**：以获准上下文与端点解析 EffectiveCapabilityPolicies；Header 默认继承在 domain 完成（默认凭据转发为全剥离，无端点级默认值）。超时与 Header 节点随后进入 `FORWARD` 相执行（见 §5.1、§5.2）。
- **能力执行（POST_AUTH 槽位）**：统一能力服务内部使用同一解析器与规划器；限流定义依据获准事实生成 App、已验证 Client、Subject、Endpoint 以及新匹配 IP 单元，通用规划器排除已执行键。维度生成不属于阶段服务。
- **headerPreparation**：按命中的 HeaderConfig 定稿凭据清除计划与可信 Header 取值；固定安全规则不可关闭：先删除所有受保护字段，再写验证结果；来源缺失则不写入。语义详见第 6 节。

`AdmissionStageOutput(Allowed access, capabilities, execution)` 的三项均必填，只表达实际执行过准入段的评估结果；execution 仍可能拒绝。Header 生效策略由 `capabilities.get(EffectiveHeaderPolicy.class)` 唯一承载，不复制第二份；FORWARD 的 Header 能力据此产出转发计划，获准上下文与原始证据保留在 access 中。

capabilities 与 execution 直接取自同一次 `CapabilityExecutionOutput`；Header 后续消费不重新解析。能力命令的三个字段均必填，执行键集合允许为空并防御性复制。Assembler/Planner 继续保留 domain 纯规则职责；应用服务只统一调用顺序、批次执行及取消／异常，不引入额外转发服务。

**组合入口**：`IngressAdmissionCommandService.admit(AccessStageCommand, subjectAuth)` 串联 access → admit，负责拒绝短路、已执行配额记录交接及跨阶段取消／异常传播。其返回 `IngressAdmissionOutput`：`AccessDenied` 包裹接入拒绝；`Evaluated` 包裹非空 AdmissionStageOutput，不等于配额已允许。接入拒绝不会构造准入段输出或调用准入段。

三个业务阶段对应两个协议接入点：准备过滤器在安全链与路由匹配之前；`IngressAdmissionGlobalFilter` 在 SCG 路由就绪后调用组合服务，处理 HTTP 响应与 Header 转发。过滤器及应用服务均在装配时连接，不逐请求创建；不是用三个过滤器强行对应三个业务阶段。

### 4.4 管理入口链

管理端点的额外授权链要求 HOST_SESSION 主体 + admin scope + gateway audience（ManagementAccessRules 在启动时对当前快照校验最低要求，运行期安全链独立执行同一领域规则）；Browser Session 不得成为管理主体。该最低授权检查本身不执行客户端签名验证、Browser Origin 校验与身份维度限流。

本地管理路由在此检查之外，由 LocalPolicyWebFilter 按 `routeId.value()` 识别后复用 IngressAdmissionGlobalFilter，消费准备结果并执行通用接入／准入流程；不能将“最低授权检查不执行配额”理解为“本地管理路由绕过准入”。

## 5. 阶段二：下游治理

只作用于获准请求，输入为两阶段交接物。本阶段执行 `timeout` 与 `headerApply`；`rateLimit` 的两个槽位属于阶段一，见 §4.1。

### 5.1 超时（v1 交付）

区分连接、响应等待、总时限；连接超时必须由 HTTP 客户端实施。响应提交后不能改写标准错误体。

**三者的实施位置**：`connect` 与 `read` 经 SCG 的 route metadata 交给 Netty（`connect-timeout` / `response-timeout`，毫秒；见 §5.3 的协议适配边界），`total` 由 Reactor `timeout` 施加于整次调用。**三者的边界必须说清**：`connect` 是**连接建立预算**；`read` 当前映射为 SCG 的 `response-timeout`，约束的是**等待下游响应的范围**，由 HTTP 客户端实施；`total` 是**整条下游调用链的总预算**——Reactor `timeout` 施加于该 Publisher，**包含其中的连接等待与响应写出，但不包含此前的认证与准入处理**。

所以「只实施 `total` 不够」的理由**不是**「total 不覆盖连接阶段」（它覆盖），而是：连接失败需要**连接超时**给出明确、可归因的终止信号；只有总预算时，超时只表现为「整条链超时」，无法区分是连不上还是响应慢。

SCG 自身的响应超时会抛出 `ResponseStatusException(504)`；该超时与我们自己的 `total` 超时均由协议适配层映射为 HTTP 504。已提交响应时不替换原响应。

### 5.2 Header 转发应用（v1 交付）

headerApply 执行准备段定稿的计划：发送下游前清除所有受保护字段，再按当前策略写入可信结果；来源缺失则不写入（信任语义见第 6 节）。客户端版本仅清洗后声明，不参与授权。历史名称与其他未注册自定义名称一样处理。

v1 已交付并**能力化**：计划由 `HeaderApplyCapability` 在 `FORWARD` 相声明为单元，由 `IngressAdmissionGlobalFilter` 在发起调用前应用（计划缺失即失败关闭）。应用时点因此晚于准入、早于下游调用——被拒绝的请求不会被改写，而 `FORWARD` 相消费的仍是**清除前**的请求证据。

### 5.3 能力执行契约

能力的三个维度相互独立：Slot 定义固定执行时机，Fact 表示输入可用性，Outcome 承载本次处理的产出与处置。能力专属业务规则由各能力自己的领域模型判定，不扩充为通用条件表达式。

**执行点只有三个**，由代码固定顺序，不接受配置重排：

| Slot | 时机 | 构造不变量 |
| --- | --- | --- |
| `PRE_AUTH` | 准备段，身份验证及接入评估之前 | evidence 必填，endpoint 可空，granted 必须为空 |
| `POST_AUTH` | 接入评估放行后，在准入段执行能力 | evidence、endpoint、granted 必填 |
| `FORWARD` | 准入放行之后、Header 应用和实际转发之前 | evidence、endpoint、granted 必填 |

POST_AUTH 和 FORWARD 共享身份事实，但承担不同执行职责；后者不表示所有转发指令已应用。

**Fact 只表达可用输入**，由 `CapabilityContext.facts()` 从不可变上下文推导，调用者不能独立设置：

| Fact | 准确定义 |
| --- | --- |
| `ENDPOINT` | 已得到唯一端点，不表示访问已获准 |
| `ACCESS_GRANTED` | 接入评估已经放行，获准上下文存在 |
| `VERIFIED_CLIENT` | 获准上下文实际携带已验证 Client |
| `AUTHENTICATED_SUBJECT` | 获准上下文实际携带已认证 Subject |

evidence 对所有上下文必填，因此不提供恒真的 REQUEST；执行点已表明转发前时机，因此不提供重复且暗示已就绪的 FORWARD_READY。

匿名放行具有 ACCESS_GRANTED，但可以没有 VERIFIED_CLIENT 和 AUTHENTICATED_SUBJECT。可选事实缺失时，对应能力或单元不适用，规划器跳过；阶段必需输入缺失时，Context 构造失败，不能通过跳过安全能力继续转发。默认 `requiredFacts()` 为空集合，表示除上下文不变量外无附加输入要求；非空集合为 AND 条件。能力专属业务判定不属于 Fact。

**执行定义与单元契约**：slots 为非空不可变集合，requiredFacts 为非 null 不可变集合，均不含 null 元素；units 返回非 null 不可变列表且不含 null 单元。unitId 为非空白、在能力内唯一寻址的身份，同一请求跨槽位保持稳定；单元相等性须覆盖载荷和事实依赖。本次生成的相等同名单元可折叠，同名但载荷或事实依赖不同的单元必须拒绝，即使该键已经执行或其事实尚不可用也不能静默跳过。该检查与跨槽位的已执行键去重分别处理。

**规划与执行**：`PlannableCapability` 是唯一规划契约，声明 slots、requiredFacts、repeat 并产生 units。planner 按固定顺序检查事实和执行键；应用服务先校验完整计划与执行器类型，再执行批次、处理拒绝及取消。限流位于 PRE_AUTH/POST_AUTH；超时预算和 Header 应用计划位于 FORWARD。同一规则跨阶段去重，Header 计划缺失时接口层失败关闭。

**配置与计划分离**：Assembler 只接收 policyConfig、endpoint、granted，不依赖 CapabilityContext 或 Slot。EffectiveCapabilityPolicies 表示本次生效策略，CapabilityPlan 表示当前执行点的执行单元。application 的 `CapabilityRegistration<P>` 配对能力与 `Function<EffectiveCapabilityPolicies, P>`；每次规划绑定当前策略，生成直接持有非空 P policy 的请求级 Entry，再交给 domain Planner。策略来源通过策略 Class 类型化读取显式传入的容器，不读最新快照、ThreadLocal 或共享可变字段；不缓存请求级 Entry，不使用 Supplier 捕获请求。未配置状态在具体策略内部表达，不能用空 Entry 跳过安全能力。

**不划分单槽位／多槽位接口**：配置数量、单元数量与槽位数量相互独立。限流 List 表达配置累加，不表达执行阶段；Header 单 Config 表达整体覆盖。统一 PlannableCapability<P> 通过 slots() 声明槽位，units(policy, context) 生成单元，Planner 负责事实筛选及去重。

**装配完整性**：CapabilityExecutionCommandService 构造时调用 CapabilityRegistration.validate，双向校验注册项与执行器标识集合，拒绝重复能力和重复执行器；所有调用方都受约束，不仅是 Spring 装配。生产装配另校验策略组合器与运行能力的标识集合一致，协议编解码注册也必须完整覆盖同一配置目录。真实装配契约测试验证当前三个能力及其配置来源，防止注册项和执行器同时漏注册；逐请求仍在首个副作用前校验整个计划的执行器及单元类型。不增加动态插件注册机制。

**解析结果复用**：PRE_AUTH 与 POST_AUTH 分别按当时的可信事实解析。FORWARD 使用准入段同一份 EffectiveCapabilityPolicies，通过 executeAgainst 规划当前执行点，不重读快照或重新解析绑定。

**Outcome 契约**：只保留 `disposition()`，取值为 `CONTINUE / STOP`；不提供 kind 或产出分类枚举。CONTINUE 继续当前执行点的普通能力链，STOP 停止普通链并由具体 Outcome 的适配器处理响应，不等于业务拒绝，也不跳过必需的资源清理。拒绝原因、重试时间或正常应答内容由具体强类型 Outcome 表达，不是调度状态。限流允许为 CONTINUE，超限及配额不可用为 STOP；Header 与超时 Outcome 为 CONTINUE，但携带的计划仍必须在转发前应用。执行 key 只服务单元去重，不证明副作用成功或指令已应用。一个 Outcome 表达本能力本次执行批次的聚合产出，不要求与单个 Unit 一一对应。

**协议适配边界**：接口层在 FORWARD 后应用 Header 计划及路由连接/响应超时元数据，总预算包裹下游调用链。未提交响应时，已识别的超时映射 504，连接异常映射 502；已提交响应及未分类异常继续传播，不替换原响应。SCG 的 RequestRateLimiter/RedisRateLimiter 不与本限流链叠加，以免重复扣减。

## 6. Header 与信任上下文

**本节描述的 `HeaderConfig` 是 v1 交付能力 `headerApply` 的配置节点**（应用时点见 §5.2）——它以 `header-apply` 注册到开放的 Bundle 中，凭据转发设置、可信字段选择与部署准入规则都在 v1 交付范围内。

**CredentialForwarding**（凭据转发设置值对象）包含 `authorizationHeader`、`cookies`、`allowedCookieNames`，由 `HeaderConfig` 引用。

- v1 取消端点级默认转发设置，`HeaderConfig` 是唯一来源，未配置时默认全剥离。
- `ClientAccess.credentials` 仅表示签名凭据，两者不混用。

**HeaderConfig 构成**：

| 组成 | 取值 |
| --- | --- |
| `trustedHeaders` | `Set<RequestHeader>`，固定输出集合的选择 |
| 普通字段 | 删除 / 受控设置 |
| `authorizationHeader` | STRIP / PRESERVE |
| `cookies` | STRIP_ALL / ALLOW_LIST |

默认删除凭据。构造时校验：HTTP token、名称长度 128、值长度 4096、控制字符、大小写重复、跨操作冲突；允许空字符串值；普通 set/remove 目标不得是已登记的 `RequestHeader` 名称。

**未命中任何 `HeaderConfig` 时的默认计划**是 `HeaderConfig.defaults()`：可信字段选择全部受支持输出，凭据转发全剥离。headerApply 的目的就是向下游建立可信身份上下文（§5.2，安全必需项），凭据清除随之默认开启，因此默认计划下发全部已验证身份；需要收窄时在 Bundle 显式声明更小的 `trustedHeaders` 集合，整体覆盖语义不变。

`HeaderConfig` 将普通 `setHeaders` 的 key 与 `removeHeaders` 的元素统一规范化为小写后保存为不可变集合；规范化后的重名即使值相同也拒绝，不覆盖或折叠。名称首尾空白仍非法；值不修剪、不变更大小写。值校验内嵌于构造逻辑，在不可变复制之前执行；不保留独立 HeaderValueRules。仅名称大小写不同的配置具有相同领域表示、相等性与规范化序列化结果。`trustedHeaders` 继续使用 RequestHeader 枚举。

**framework-core RequestHeader**：

- 只登记有限的共享自定义 Header（CONTEXT、SIGNATURE），不登记 Host、Content-Length 等标准协议字段。
- 不设 LEGACY 分类或历史名称别名。
- 字符串消费点使用 `headerName()`；`fromName(name)` 验证 HTTP token 后忽略大小写查找，不修剪非法空白；`fromType(HeaderType)` 派生不可变集合。
- gateway `HeaderConfigRule` 独立校验配置名称并规范化。

**trustedHeaders 的固定输出**由 `TrustedHeaderResolver` 统一解析，写入其 `headerName`，禁止重命名映射：

| 输出 Header | 来源 |
| --- | --- |
| APP_ID、APP_PLATFORM | VerifiedApp |
| CLIENT_ID | 已验证的 VerifiedClient |
| USER_ID、SESSION_ID、SUBJECT_TYPE | AuthenticatedSubject |

APP_VERSION 和 SIGNATURE 字段不可选择。framework 不依赖网关上下文。动态更新只改变固定可信字段的选择和普通字段操作，不提供名称映射。

Header 值解析统一位于 domain 的 capability.header 包：TrustedHeaderResolver 从 AccessGrantedContext 生成可信值；DeclaredHeaderResolver 接收 RequestHeader 与原始字符串，仅解析明确支持的声明字段，当前只支持 APP_VERSION（1–64 个 ASCII 字母、数字或 `._+-`）。缺失、非法或不支持的声明返回空，不修剪、不作为认证证明。两者均提供 supports、supportedHeaders 与 resolve，不依赖 HTTP 类型。interfaces 负责读取请求及绑定／写入，两个入口复用同一声明解析器；可信字段不允许回退到声明值。Trusted／Declared 表示值来源，而非 RequestHeader 的永久分类。

**HeaderConfigRule**（不可变部署否决规则，`*ConfigRule` 家族：非快照内容的判定器，由 `gateway.header` 的 `deny-set` / `deny-remove` 在启动时生成）：

- deny 名单在构造时经 `RequestHeader.normalizeName` 校验并规范化为小写存储；非法 token 字符、超长名称在构造即拒绝，重复名称折叠为同一项。
- 普通 set/remove 目标在 `HeaderConfig` 构造期即拒绝已登记的 `RequestHeader`（身份与签名字段），部署配置不能放宽；`HeaderConfigRule` 独立复核同一约束。
- `deny-set` / `deny-remove` 按操作独立否决；名单之外的合法 token 名称均可作为普通 set/remove 目标。不设部署扩展名单或网关前缀准入。
- 规则不写入业务快照或其内容摘要。
- 当前规则在启动期固定，`HeaderConfig` 随快照动态更新；不通过异步修改 Properties 放宽运行请求。未来若支持规则刷新须先校验当前策略并整体切换。
- 运行期写入前，入站 `X-GW-` 前缀 Header 与全部已登记 `RequestHeader` 一律清除再按计划写入（见 §5.2），客户端不能借网关命名空间伪造身份。

**其余约束**：校验名称、重复目标、CR/LF、长度和协议控制字段；未验证 Client 声明不写 CLIENT_ID；不提供历史名称识别、专门清除或兼容转换，历史名称与其他未注册自定义名称一样处理。

## 7. 快照与发布

**入口快照 `IngressSnapshot`** 由 `SnapshotVersion`、`IngressContent`（App/Client/Endpoint 注册表与 CapabilityPolicyConfig）、`contentHash` 与 `publishedAt` 构成，不可变。它服务整个入口管道，而非仅 access 阶段，因此使用 Ingress 前缀。

- `contentHash` 计算规范化业务内容，不含版本和 `publishedAt`。
- 一个请求只读取一次快照。

**快照自身的构造不变量**：

- Client 引用的 App 必须已登记。
- active 与 grace 凭据都必须在 `publishedAt` 时刻处于有效期内。
- `credentialId` 在全部 Client 间唯一。
- `credentials` 为 null 的 Client 跳过凭据校验。
- 任何校验失败以 `ACCESS_SNAPSHOT_INVALID` 拒绝，配置错误不静默降级。

Bundle、Binding 与 Header 组合由 `CapabilityPolicyConfig` 建模。内容校验 `validate(policyConfig)` 检查注册能力、配置类型及能力专属规则，适用于 Definition 保存、编解码和请求组装。完整校验 `validate(policyConfig, apps, clients, endpoints)` 还检查同一内容中的绑定引用（`AppId`、已验证 `ClientId` 必须存在）、受影响端点及同层竞争节点冲突；发布组装、完整快照编码和解码均须执行完整校验，Nacos／LKG 解码成功后才允许切换运行快照。快照构造器只持有上述自身不变量，不替代完整组合校验；摘要正确也不能替代配置有效性校验。

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

- 先验证目标存在、HeaderConfigRule 和完整发布有效性，再于首次恢复写入前激活阻断。
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

不同实例允许短暂相邻版本；不承诺全局瞬时撤销。令牌余额是执行状态，不作为快照数据；无关发布不重置状态。

## 8. 管理面接口

管理面分为 definition 与 snapshot 两组、各分 command 与 query 共四个门面（`api/facade/admin/{definition,snapshot}`）：命令门面仅在存在可写 Definition 存储时装配，查询门面在只读部署同样可用。运行时请求路径不经过管理门面。

**两个版本轴独立前进**：

| 轴 | 粒度 | 性质 |
| --- | --- | --- |
| `sourceRevision` | Definition 文件级令牌——App、Client、Endpoint、能力组合各一个 | 由内容摘要派生、不透明、随整份文件前进，不随单条目孤立存在 |
| `snapshotVersion` | 全局发布版本 | 每次发布前进 |

两个版本轴不能相减或比较大小；“已保存未发布”应比较当前 Definition 组装内容与发布内容的规范化摘要。CAS 粒度是文件，命令粒度是意图。读取时由 `RevisionedAccessDefinition`（值 + 文件级 revision）与 `RevisionedAccessSnapshot`（快照 + 源 revision）成对携带，后续 CAS 不能拿值配另一份令牌。

**Definition 查询**：`getApps`/`getClients`/`getEndpoints` 按集合返回全量内容与该集合共享的 sourceRevision；revision 为 null 表示该 Definition 尚未初始化。查询不回显 secretRef，凭据只回显 credentialId、协议与有效期供编辑参考。

**Definition 命令**（全部返回保存后的新 sourceRevision，供连续编辑免重查）：

- `saveApp`/`saveClient`/`saveEndpoint`：单条内容 upsert，不含 status。创建以 ENABLED 初始化，已有实体的 status 不被 save 改变——发布才是生效闸门，"预置配置、暂不发布"即预置工作流。saveClient 只动接入设置、绝不触碰凭据；saveClientCredential 整对轮换（grace 缺省即清除），要求 Client 已存在，凭据不随接入编辑往返。
- `setAppStatus`/`setClientStatus`/`setEndpointStatus`：唯一的上线/下线入口，载荷仅 id + status，与内容编辑分属不同授权与审计事件。
- `saveCapabilityPolicyConfig`：完整替换能力组合 Definition，输入沿用 `SaveCapabilityPolicyConfigApiCommand(expectedSourceRevision, policies)`，`policies` 包含完整 bundles／bindings。Bundle 内为 `capabilities` 对象，按注册标识解码为强类型配置。允许空 bundles + 空 bindings 清空编辑态；不提供单个 Bundle 的 PATCH 或运行时插件安装。
- 能力组合保存前校验 Bundle／Binding 键与各自 ID 一致、规范化标识判重、Binding 引用、能力类型与专属跨 Bundle 约束，以及 Header 部署准入；对 App／Client／Endpoint 的跨 Definition 引用及同层重叠绑定竞争仍在发布时以完整内容校验。保存成功只返回 DefinitionSaveApiOutput(sourceRevision)，不产生新 snapshotVersion，不改变已捕获快照。
- 删除不提供：下线一律走 status；物理清理与批量迁移走 Nacos 侧整体重建 Definition（绕过 API，令牌随之变化，之后经发布生效）。
- `expectedSourceRevision`：当前管理命令要求非空白且必须与最近读取或保存返回的令牌一致，不提供 null／空令牌创建 Definition 的语义。保存复用 AccessDefinitionWriter：在既有发布锁保护下检查回退阻断、核对 revision 并执行存储 CAS；旧 revision 或 CAS 失败均拒绝，不自动重试覆盖。同一文件的编辑互相冲突，Client 的接入设置与凭据轮换共享文件；不同 Definition 拥有独立 revision。

**Snapshot 门面**：`release` 显式发布，不携带调用方令牌（发布内部读取全部 Definition、组装校验、再次确认 revision 后 CAS 发布）；`rollback` 回退到正数的历史版本；`get` 返回当前生效快照，从未发布时字段为 null。发布与回退的锁顺序与阻断语义见第 7 节。

## 9. 分层与模块职责

domain 持有模型与纯规则；application 编排用例与端口；infrastructure 实现安全/存储/配额；interfaces 处理 HTTP 生命周期；boot 装配。不建设动态插件或脚本平台。

包边界：`application.configuration.{definition,editing,publication}` 管理编辑态与发布；`application.ingress` 编排运行期三段；`domain.ingress.access` 持有访问决策和身份事实；`domain.ingress.snapshot` 持有完整快照与版本接纳规则；领域能力模型位于 `domain.capability`（configuration / runtime / ratelimit / resilience / header / catalog 按职责分包）。配置管理不再使用含糊的 application.access 包，快照不再混入访问规则包。

能力包内：根包持有配置标识、CapabilityConfigRule 与策略组合器契约；configuration 持有 Bundle、Binding、PolicyConfig、CapabilityPolicyConfigValidator、Assembler 和 EffectiveCapabilityPolicies；runtime 持有能力定义、请求级规划项、上下文、计划、执行记录与具体能力的单元／结局。ratelimit、resilience、header 持有各能力的纯配置、策略组合器及业务规则；catalog 固定登记内建策略组合器并提供 Validator／Assembler 工厂，运行装配先创建共享 Validator，再注入 Assembler（代码登记，不由快照配置驱动：组合只能使用已登记能力节点，目录本身不随配置变化），通用容器不依赖该目录。runtime 与 configuration 互不引用，都可依赖纯能力模型；由 application.ingress 的 CapabilityRegistration 连接二者。组合的跨注册表引用与同层竞争校验收口在 CapabilityPolicyConfigValidator.validate(policyConfig, apps, clients, endpoints)；CapabilityPolicyConfig 不依赖服务，也不反向依赖包含它的 IngressContent。runtime 仍是纯领域模型，不持有线程、容器或 HTTP 执行器。

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
| 领域内容 → Canonical 内容 | 规范化编码边界：排序、字段格式、空值省略及时间编码保持显式语义；其中可分离的结构复制复用 mapper。规范化内容随明确的 schema 变更更新，其他字段的排序、格式与语义不变。 |

契约与 MapStruct 实现分开：契约按转换职责命名；实现类置于契约包的 mapstruct 子包，使用 `*MapStructMapper`，实现契约并声明 `@Override`。共享配置在所属模块内复用，保持 Spring component model 与未映射目标字段报错；局部投影显式声明忽略字段或白名单，不降低全局约束。不同外层模块不为复用配置而引入反向依赖。

生产链通过容器注入 mapper 契约，MapStruct 子映射通过 `uses` 复用；不在业务对象或 mapper 内自行获取另一份非托管 mapper，也不以匿名实现承载整套手写结构复制。自动配置与组件扫描必须保证同一契约的装配一致。

**开放配置的协议边界**：API Bundle 和存储 Bundle 使用 capabilities 容器；协议树只存在于外层，进入领域前必须按已注册类型解码。CapabilityConfigCodec 绑定策略组合器、存储 DTO 与双向转换；结构投影由 CapabilityNodeStorageMapper 的 MapStruct 实现完成，领域重建显式调用构造规则。未知能力、未知嵌套字段、非对象节点均拒绝，配置不接受任意类名。存储按能力标识排序，各能力的编码契约负责其集合规范化。配置保存、编码／加载和发布都保留能力专属校验及 Header 部署准入。

能力组合保存链为 HTTP Request → GatewayPolicyAdminRestMapper → AccessDefinitionCommandFacade.saveCapabilityPolicyConfig → CapabilityPolicyConfigApiMapper → CapabilityPolicyConfigCommandService.save。Controller 只绑定请求并调用 Facade；MapStruct 完成结构映射，标识重建与注册解码为显式语义转换。接口层复用 infrastructure 的共享 CapabilityConfigCodecs，不维护另一套能力类型列表，也不依赖 Nacos 仓储。interfaces → infrastructure 为单向的外层编解码复用，API／domain／application 均不反向依赖该组件。非法 JSON 节点形状／未知能力转换为框架字段校验错误，领域不变量与应用冲突保留各自错误归属。

HTTP 与存储节点均按注册的 DTO 严格解码；只有本地 Properties 适配允许把数字索引对象还原为集合。Definition 写请求使用 `@ClientRequestBody`，由 ClientRequestBodyArgumentResolver 读取 body、覆盖可信 Client／会话上下文，再触发校验；避免标准 `@RequestBody` 解析器提前截获请求。body 的同名身份字段不能建立权限。写 Controller 由可写用例条件装配，使用 WebFlux 可识别的 Controller 标记，并从启动组件扫描排除以防越过条件。

当前实现：HTTP 使用 `GatewayPolicyAdminRestMapper`，定义及快照 Facade 分别使用 `AccessDefinitionApiMapper`、`AccessSnapshotApiMapper`；App、Client、Endpoint 和能力组合的 Properties 各有独立转换契约。`DefaultStoredAccessSnapshotMapper` 保留领域重建、路径校验及注册表排序，字段投影委托 `AccessSnapshotDocumentMapper`，后者通过 `uses` 复用 `CapabilityPolicyConfigStorageMapper`。`DefaultCanonicalPolicyContentMapper` 保留规范化编码并注入能力组合 mapper，不自行构造存储 mapper。interfaces 与 infrastructure 分别复用模块内的严格配置，自动配置仅扫描各自 mapper 实现包。

领域构造、状态转换、授权决策、签名编码及用例结果的语义组合继续使用显式方法。特别是新建聚合的状态、修改时保留的凭据、Header 默认与可信身份选择，不能由自动映射替代其领域／用例规则。`CapabilityExecutionOutput` 组合本次解析结果和实际执行记录；阶段交接仍保留同一份快照与事实，不为了引入 mapper 而重新解析、复制另一套上下文或增加纯转发层。

## 10. 错误及破坏式更新

EDGE 前缀 800：000–099 共享领域；100–149 快照；150–199 端点注册；200–249 App；250–299 Client；300–349 签名验证（现有 SIGNATURE_VERIFIER_NOT_FOUND 为 300）；350–599 未分配领域；600–699 应用；700–799 自有技术；800–899 外部适配；900–999 保留。已分配编号与语义不复用。

基础设施错误枚举按"拥有该语义的 module"声明：`INFRA_TECH_SECRET_NOT_FOUND(702)` 属 security-spring 模块（`SecuritySpringError`）、`INFRA_TECH_POLICY_ROLLBACK_STATE_FAILED(705)` 属 coordination-redis 模块（`CoordinationRedisError`）、`INFRA_ACL_NACOS_PROVIDER_UNAVAILABLE(802)` 属 persistence-nacos 模块（`PersistenceNacosError`）。`INFRA_ACL_NACOS_CONTENT_INVALID(803)` 保留在基础 infrastructure：Nacos 入口策略内容校验由基础 infrastructure 的共享存储编解码机制（`StoredAccessSnapshotCodec`、`EndpointPathPatternValidator`）与 persistence-nacos 模块共同引用，而基础 infrastructure 不得反向依赖具体存储模块。死码 `INFRA_ACL_ACCESS_TOKEN_PROVIDER_UNAVAILABLE(800)`、`INFRA_ACL_BROWSER_SESSION_PROVIDER_UNAVAILABLE(801)` 无抛出点，按错误段归属保留原聚合，不重排。

共享领域仅保留 000（实体 ID 非法，`DomainException.invalidEntityId()`）。历史编号 1/2/3（对象字段缺失、对象字段非法、对象状态异常）已退役且空置不复用；真正的技术性前置条件（缺少内部参数、技术容器的 null 元素／键／值、Java 类型与注册元数据错误）使用技术异常，不占用领域业务错误面；Require.notNull/allNotNull 仅用于构造器或公共方法参数，集合元素通过 NullSafe 检查并抛 FrameworkException。业务不变量失败由领域显式 guard 或 Require 业务异常供应器表达，不能仅因使用了谓词或跨字段判断就归为技术异常。完整快照的绑定引用及同层竞争继续使用 ACCESS_SNAPSHOT_INVALID（102）。

本轮在原拥有范围内追加以下错误，不改变任何已有编号或模板：

| 本地码 | 名称 | 语义 |
| --- | --- | --- |
| 103 | ACCESS_SNAPSHOT_POLICY_INVALID | 策略业务规则非法，包括重复配置身份、未登记能力、Header 准入、限流约束与超时范围 |
| 253 | CLIENT_CREDENTIAL_INVALID | 凭据 validFrom 不早于 expiresAt |
| 254 | CLIENT_CREDENTIAL_ROTATION_INVALID | active/grace 身份重复或签名协议不一致 |
| 613 | APP_POLICY_OPERATION_TIMED_OUT | 发布／回退预算耗尽；检查当前状态后重试，不保证远端没有提交 |

WebFlux 请求绑定和 Spring 代理的 Bean Validation 失败由统一 GatewayValidationExceptionHandler 翻译为 framework OBJECT_INVALID。受信任 Client 上下文先绑定，再触发请求对象校验；Facade 的接口约束由 @Validated 实现的 Spring 代理执行。

开发阶段允许直接更名 Java/API/配置及重建开发快照，不保留旧 schema 别名层；历史签名算法保留。
