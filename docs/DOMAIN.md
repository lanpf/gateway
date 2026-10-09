# gateway 领域设计 · v1

本文定义 gateway 入口安全与策略治理领域当前认可的概念、模型与决策规则。服务边界和交付限制见 [职责文档](RESPONSIBILITIES.md)，报文与编码见 [协议文档](PROTOCOL.md)，专属实现决策见 [技术设计](DESIGN.md)，工程运行见 [README](../README.md)。

本领域将原始请求事实、受管理入口配置和认证结局转为接入决策、适用配额及转发治理计划。入口身份与最终资源授权是不同的决策。具体服务所有者见职责文档。

一次请求的生命周期分为两个宏观阶段：**阶段一入口准入**与**阶段二下游治理**。阶段一完成身份验证、接入评估与限流；阶段二实施已获准请求的超时预算与 Header 转发应用。

## 1. 请求生命周期：两阶段与能力库

### 1.1 阶段一：入口准入

决定放不放行、以什么身份放行、扣多少额度，全部发生在代理之前。三段固定管道：

1. **准备段（prepare）**：捕获单次快照与原始请求事实，取得技术路由候选，执行认证前 IP 限流。此段不依赖任何身份——未知路径与歧义候选同样受保护。
2. **接入段（access）**：主体认证与客户端签名验证协作，端点/App/Client/Subject 接入评估，产生接入决策与获准上下文。
3. **准入段（admit）**：使用获准上下文解析能力绑定，产生 EffectiveCapabilityPolicies，执行认证后限流，定稿 Header 准备（凭据清除计划与可信 Header 取值）。

### 1.2 阶段二：下游治理

只作用于获准请求：实施连接、响应等待与总时限，应用 Header 转发计划。被拒绝的请求不产生任何下游动作。

**交接物**：入口准入向下游治理移交获准上下文（AccessGrantedContext）、EffectiveCapabilityPolicies（限流、超时、Header 有效配置）、配额执行记录、Header 准备结果与捕获的原始证据。转发前保留捕获的原始请求证据；Header 安全清除属转发必需项。

### 1.3 能力库与固定拓扑

能力按输入事实、产出、失败语义和执行时机定义。配置能力包括限流、超时和 Header 应用；固定执行点及事实约束见第 5 节。

限流使用认证前、认证后两个槽位，同一规则跨阶段最多执行一次。安全拓扑不能由调用方重排：认证前 IP 保护覆盖未知路径；可信身份只能在验证及接入评估之后参与绑定；Header 安全清除和可信写入必须在转发之前完成。接入判定的固定顺序还避免向未认证调用方泄露探测差异。

组合由 Bundle/Binding 承接，见第 3 节。入口链与管理链具有各自固定的安全要求，管理要求见第 4.4 节。

## 2. 统一语言与模型

本节是词汇表与模型契约。2.1–2.6 按领域分区列出模型，每条只写"它是什么、不变量由谁持有"；2.7 给出跨模型规则，2.8 展开接入评估的固定顺序。

### 2.1 登记载体与聚合

| 模型 | 定义 |
| --- | --- |
| App / AppId | 已登记应用载体。声明 App 不等于证明身份。 |
| AppAccess | App 的接入属性值对象，当前仅含规范化 allowedOrigins。 |
| AppRegistry | App 的登记集合；重复 AppId 以 APP_AMBIGUOUS 拒绝。 |
| Client / ClientId | 接入主体。 |
| ClientAccess | Client 的接入属性值对象：allowedApps 与可选 Credentials。 |
| ClientRegistry | Client 的登记集合；重复 ClientId 以 CLIENT_AMBIGUOUS 拒绝。 |
| Endpoint / EndpointId | 端点。拥有逻辑服务归属、逻辑交付路由、端点匹配器。 |
| EndpointMatcher | (httpMethod, pathPattern) 精确匹配器；method 大写化，两者均去除首尾空白。 |
| EndpointAccess | 端点接入策略：clientVerificationRequirement、subjectAuthenticationRequirement、requiredScopes、requiredAudiences。 |
| EndpointRegistry | Endpoint 的登记集合；重复 EndpointId 或重复 EndpointMatcher 以 ENDPOINT_AMBIGUOUS 拒绝。 |

App、Client、Endpoint 各自以唯一 ID 确定身份，是状态和内容更新的一致性边界。创建默认 ENABLED；内容编辑保留原状态，Client 接入编辑还保留凭据；状态切换和凭据轮换分别表达独立业务意图。读取和保存过程不能自行改写这些保留规则。

身份相等不表示配置一致。EndpointMatch 只折叠身份和全部配置都相同的候选；同 ID 的不同匹配器、状态或接入配置仍为 Ambiguous。回退按完整配置是否一致决定是否跳过已恢复的 Definition。

标识来源如下，gateway 不自行生成登记身份或认证身份：

| 标识 | 来源及维护方 |
| --- | --- |
| AppId、ClientId、EndpointId | gateway 受管理 Definition，由配置维护方提供稳定字符串 |
| CredentialId | Client 凭据配置，由配置维护方提供并在轮换时保持两份 ID 不同 |
| CapabilityBundleId、CapabilityBindingId、RateLimitRuleId | 策略配置维护方提供；配额规则 ID 跨快照更新保持稳定 |
| CapabilityId | 受支持能力的登记标识；配置只能选择已支持的精确名称 |
| ServiceId、RouteId | 受管理的逻辑服务与交付路由标识 |
| SubjectId、SessionId | 经过认证的 Token 或 user-auth 验证响应恢复；gateway 不自行生成 |

### 2.2 凭据与签名协议

| 模型 | 定义 |
| --- | --- |
| Credential / CredentialId | 归属于 Client 的签名凭据值对象，内嵌于 ClientAccess；含 secretRef、signatureContract、validFrom/expiresAt，validFrom 必须早于 expiresAt。 |
| Credentials | active 与至多一个 grace 凭据，表达密钥轮换；两者必须同一协议且 credentialId 不同。 |
| SignatureContract | 四种签名协议枚举；协议由受管理凭据指定，不能由请求自由选择或失败后降级。 |

### 2.3 主体与认证

| 模型 | 定义 |
| --- | --- |
| SubjectType | HOST_SESSION / BROWSER_SESSION；会话家族由请求传输证据选择，见协议文档。 |
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
| Revision | Definition 文件级源修订令牌，非空白字符串；不透明，只用于 CAS 比较。 |
| SnapshotVersion | 全局发布版本，正数；0 非法。 |
| IngressContent | 快照内容：App / Client / Endpoint 三个注册表与 CapabilityPolicyConfig。 |
| IngressSnapshot | 唯一版本化的不可变入口快照：version、content、contentHash、publishedAt。 |

### 2.6 能力配置与执行概念

| 概念 | 定义 |
| --- | --- |
| CapabilityPolicyConfig | 同一快照内 Bundle 与 Binding 的完整配置；身份唯一，绑定引用必须存在。 |
| CapabilityBundle / CapabilityBundleId | 有独立身份的能力配置集合；每种能力零或一份，内容在请求期间不变。 |
| CapabilityBinding / CapabilityBindingId | 以端点范围及可信身份条件引用 Bundle。 |
| CapabilityId / CapabilityConfig | 稳定的能力标识及对应配置；未知能力或不匹配配置拒绝。 |
| EffectiveCapabilityPolicies | 当前可信事实下的生效策略；不包含执行状态，也不表示已经执行。 |
| CapabilityContext / CapabilityFact | 本次执行点的请求事实、端点和获准身份；区分尚未认证与已获准但无主体。 |
| CapabilityPlan / CapabilityUnit | 当前执行点适用的能力单元及事实要求；单元在能力内有稳定身份。 |
| CapabilityExecution / CapabilityExecutionKey | 已完成单元的累计执行键与当前执行点产出；键由能力标识、单元标识及适用的槽位组成。 |
| TimeoutConfig / HeaderConfig | 无独立身份的单值能力配置，命中后整体覆盖。 |
| RateLimitConfig | 限流规则组及其故障方式；每组保留各自的故障方式。 |
| RateLimitRule / RateLimitRuleId | 可独立寻址的配额规则，稳定身份参与配额键。 |

能力的配置合并、执行时机、事实依赖、单元身份与重复策略分别定义，彼此不能替代。只有一个单元或没有可选项的能力也遵循同一规划与执行规则。

### 2.7 跨模型规则

**标识与匹配**

- `serviceId` 是逻辑服务归属，`routeId` 是逻辑交付路由。
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
- 使用 `EndpointStatus`（ENABLED/DISABLED），与 `AppStatus`、`ClientStatus` 保持显式状态建模。
- 凭据转发设置由 `HeaderConfig` 承载。

**clientVerificationRequirement（REQUIRED/OPTIONAL）**——对验证结果的策略：

- REQUIRED：必须声明 Client，且 Client 验证结果必须为 Verified。
- OPTIONAL：允许不声明 Client；一旦声明，仍检查登记、状态及 App 授权。
- 无论端点要求如何，提交的验证结果一律按结果处理：Verified 必须与声明一致，否则 `CLIENT_VERIFICATION_MISMATCH`；未声明却携带验证结果同样 MISMATCH；Rejected 拒绝；Failed（验证依赖不可用）拒绝，绝不置空放行。
- 端点不支持 FORBIDDEN 或独立验证模式；签名验证只能由受管理凭据的协议发起，不能由请求自由选择或失败后降级。

**subjectAuthenticationRequirement（REQUIRED/ANONYMOUS）**——对认证结果的策略：

- REQUIRED：必须提供已认证主体。
- ANONYMOUS：未提交 Token（Absent）匿名放行；已证明可信、仅过期的 Token（EXPIRED）视同未提交并放行；其余拒绝原因（INVALID）拒绝。不能仅根据客户端声明的 exp 或未经完整验证的错误将结果分类为 EXPIRED；认证适配器不能证明时必须拒绝。
- 认证依赖不可用（UPSTREAM_UNAVAILABLE）与响应非法（RESPONSE_INVALID）一律拒绝，不能降级匿名或使用缓存身份。
- 端点不声明 Host/Browser 会话家族；由请求传输证据选择，表示见 [协议文档](PROTOCOL.md#主体证据)。Browser Session 不得成为管理主体，管理最低要求见第 4.4 节。

**requiredScopes / requiredAudiences**

- null 归一为空集合，非空元素必须非空白。
- 配置非空时，主体必须包含全部指定值（子集 AND 校验）。
- Browser Session 不产生 OAuth2 scope/audience（集合恒为空），为其配置非空要求即不可满足。
- 空集合不关闭 Token 认证阶段的 audience 校验，也不放宽管理入口的最低要求。

**认证结局**：区分缺失（Absent）、认证成功（Authenticated）、拒绝（Rejected）、依赖不可用（Failed）；Client 验证结局同构。`authenticationExpiresAt` 仅描述认证到期信息，不是会话绝对期限或到期前不会撤销的保证。

**AuthenticatedSubject 的校验按 subjectType 分支**。两种主体共同要求：`subjectType`、非空白 `subjectId`/`sessionId`、`authenticationExpiresAt`；`subjectId`/`sessionId` 去除首尾空白。分支差异：

| 校验项 | HOST_SESSION | BROWSER_SESSION |
| --- | --- | --- |
| issuer | 必填非空白，去除首尾空白 | 必须为空：非空白即拒绝，最终归一为 null |
| audiences / scopes | 允许 null 或空集合；null 归一为空集合，元素非空白 | 必须为空：非空即拒绝，最终归一为空集合 |

不能在分支前统一要求 audiences/scopes 非空。

**AccessGrantedContext** 仅含三个字段：

| 字段 | 类型 | 可空 |
| --- | --- | --- |
| app | VerifiedApp | 否 |
| client | VerifiedClient | 是 |
| subject | AuthenticatedSubject | 是 |

- `VerifiedClient` 含 `clientId`、`credentialId`、`verificationMode`、`verifiedAt`，只表达已通过凭据验证的 Client；请求声明的 Client 只作为入口校验输入，不进入获准上下文。
- 主体事实保留 issuer 与 authenticationExpiresAt；上下文的可信性来自认证及接入评估流程，字段完整不等于认证成功。
- 快照版本属于请求所持快照，记录在 `AccessDecision` 上，不放入身份上下文。

### 2.8 接入评估的固定顺序

`AccessEvaluator` 是无状态纯规则：输入为快照、`AccessRequest` 与两个结局（Client 验证、主体认证），输出 `AccessDecision`。前置条件由调用方保证——端点必须已唯一匹配、appId 必须已声明，否则在进入接入评估前即拒绝，分别为 ENDPOINT_NOT_FOUND 与 APP_NOT_FOUND。

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

Bundle、Binding 和能力配置按自身身份识别。元素不得缺失；重复身份即使内容相同也拒绝，不折叠或覆盖。缺失的 Bundle/Binding 集合视为空集合，Binding 引用的 Bundle 必须存在于同一组合。完整组合按全部内容比较，不以输入顺序决定相等性或优先级。协议来源键一致性和重复报文键检查见 [配置表示](PROTOCOL.md#配置表示)。

绑定优先级为 **全局 < 服务 < 标签 < 端点**，同级按 bindingId 稳定排序。可附加 AppId、已验证 ClientId、SubjectType 条件；条件采用 AND，标签全部满足，上下文条件不增加优先级。标签来自受管理配置；不支持任意脚本。

认证前只使用当时可信事实；没有唯一端点时仅匹配无身份条件的全局绑定。请求声明的 App/Client 不能充当可信绑定身份。认证后使用端点及获准上下文；转发治理复用准入段同一份生效策略，不重读快照或重新解析绑定。

| 配置 | 组合与未命中语义 |
| --- | --- |
| Header | 单值整体覆盖；未命中使用安全默认计划，见第 6 节。 |
| Timeout | 单值整体覆盖；未命中表示没有配置预算，不生成超时执行单元。 |
| RateLimit | 规则组累加，保留每组故障方式；未命中为空规则组。 |

同一 Bundle 被多条绑定命中时只保留最具体的命中位置。不同 Bundle 在同层可同时命中且竞争非累加能力时拒绝发布；同一 Bundle 的重复引用不是竞争。限流按 Bundle 去重后累加，不能丢失每组故障处理方式。

能力校验覆盖完整配置，包括未绑定或本次未命中的 Bundle；相同配置也不能因去重而漏检。单份及跨配置限流规则身份必须满足唯一性与 EXACT 约束；Header 必须满足领域安全约束及部署否决。完整发布校验还核对跨登记集合引用、受影响端点和同层竞争，见第 7 节。

省略能力键表示不声明该能力；显式 null 节点非法，不能关闭继承能力。v1 支持继承和整体覆盖，不支持局部关闭继承能力。例外必须收窄上层绑定范围或调整受管理标签；Header 的必需安全清除不依赖是否显式配置节点。

能力配置与执行定义分离。生效策略不持久化、不参与快照摘要；规划和执行只使用本次明确的策略。扩展能力不能改变既有安全顺序或通过请求配置安装任意实现。专属目录与扩展机制见 [技术设计](DESIGN.md#能力目录与校验复用)。

## 4. 阶段一：入口准入

### 4.1 准备段（prepare）

准备段捕获一次快照和原始请求事实（安全方法为 GET/HEAD/OPTIONS/TRACE），取得端点匹配候选，并在认证前执行 IP 保护。事实包括请求方法、原始路径/查询、声明 App/Client、Origin 与 remoteIp；原始签名目标不解码、不改写，缺失与空 query 区别见协议文档。签名 body 证据在匹配之后按需要采集。

路径匹配语法提供候选，领域按身份和完整内容判定 UNIQUE / NONE / AMBIGUOUS。相同候选折叠；同 ID 内容分歧或不同端点命中均为歧义。只有唯一候选贡献端点级保护；未知与歧义路径仍执行全局 IP 规则，对外不泄露差异。

PRE_AUTH 能力按固定顺序规划和执行；拒绝终止普通能力链，取消和异常终止未完成协作，不伪造允许结果。准备段成功后交接同一快照、匹配结果、原始事实和执行记录；拒绝不进入接入段。后续阶段不得重新采集事实、重读快照或补空执行记录。

**限流决策**：

- EXACT 使用共享额度，每次原子扣减；PREALLOCATED 不允许启用或静默替换。
- 全部适用规则均执行，相同维度不能只执行第一条。缺失或空白维度跳过，批次内按 RateLimitRuleId 去重；跨槽位按执行键去重。
- 认证前 IP 保护覆盖未知路径；身份维度等待获准事实。公开 App 声明不能作为唯一防滥用维度。
- 配额键使用稳定规则身份和维度，不追加 snapshotVersion；无关发布不能使额度重新满额。
- 共享额度不可用时按配置 CLOSED 拒绝，或 OPEN_WITH_LOCAL 使用有界本地应急额度；具体执行保护见技术设计，对外限制见职责文档。
- 聚合时不可用优先于超限；超限取最大等待时间；全部允许取最小剩余额度。不能因一条超限跳过其余适用规则。

准备段只因配额失败拒绝。NONE/AMBIGUOUS 在进入接入评估和触发认证之前统一拒绝 ENDPOINT_NOT_FOUND，不新增歧义拒绝原因。实际交付路由还必须与唯一候选路由一致；协议核对见职责文档。

### 4.2 接入段（access）

前置条件是准备成功、端点唯一匹配且 App 已声明。接入段协调 Client 验证和 Subject 认证，按第 2.8 节作决定；未知、歧义或缺失 App 的请求不触发认证。成功结果携带访问决定和原准备事实；拒绝在本段终止。本段不解析治理能力，也不决定配额维度。

**签名与身份建立**：

- 使用受管理凭据选定的协议，具体计算与报文见 [签名协议](PROTOCOL.md#签名协议)。在路径、查询、body 改写前验证，不能失败后降级到另一协议。
- secret 只经 secretRef 获取，不进入快照或日志；验证比较须抵抗时序泄露。
- active/grace 仅表达密钥轮换。新协议以 credentialId 精确选择有效候选；历史协议最多依序尝试两份。凭据 Client 归属须与证据一致。
- 签名成功后必须原子登记防重放；登记成功才建立已验证身份。重放按 Client+nonce 隔离，轮换不能绕过约束。
- 时间窗口及凭据有效期在同一次业务时间下判定。密钥或防重放依赖失败不能转换为匿名；空登记响应属依赖异常，不等同真实重复。
- 协议迁移与密钥轮换分别处理，请求取消传播到未完成登记。

### 4.3 准入段（admit）

只接收接入成功结果，保留同一准备事实、快照与累计执行记录，不重新认证。依获准上下文解析能力，规划 POST_AUTH 的身份维度及新匹配 IP 规则，排除已执行键，并定稿 Header 安全计划。Header 语义见第 6 节。

准入评估产出包含接入成功事实、生效策略和实际执行记录。产生该结果不等于配额允许；配额拒绝仍终止请求。Header 与超时的生效策略在 FORWARD 继续使用，不产生第二套身份上下文或重新解析。阶段组合依序协调接入和准入，负责拒绝短路、记录交接与跨阶段取消/异常。

### 4.4 管理入口链

管理端点最低要求是 HOST_SESSION、admin scope 和 gateway audience，Browser Session 不得成为管理主体。最低授权检查本身不执行 Client 签名、Browser Origin 与身份配额，但本地管理路由仍须经过完整入口准入；最低检查不能成为绕过通用接入/准入的依据。运行和启动检查范围见职责文档。

## 5. 阶段二：下游治理

### 5.1 超时

只对获准请求施加连接、响应等待与总调用预算。连接预算界定连接建立；响应预算界定等待下游响应；总预算包含下游调用中的连接等待与响应写出，不包含此前认证与准入。总预算不能代替独立连接预算。实施映射见技术设计；HTTP 映射及响应提交后的限制见职责文档。

### 5.2 Header 转发应用

转发前必须应用已定稿的安全计划；来源缺失则不写入。被拒绝请求不产生下游动作，也不被转发改写。FORWARD 消费的仍是清除前原始证据。计划缺失失败关闭，不能继续转发。具体可信来源和操作约束见第 6 节。

### 5.3 能力执行契约

执行时机、事实可用性与本次产出分别定义；各能力保有自己的业务判定，不扩展为通用条件表达式。

| 执行点 | 时机与必需事实 |
| --- | --- |
| PRE_AUTH | 身份验证及接入评估前；原始证据必需，端点可缺失，获准上下文必须缺失。 |
| POST_AUTH | 接入评估放行后；原始证据、唯一端点、获准上下文必需。 |
| FORWARD | 准入放行后、实际转发前；原始证据、唯一端点、获准上下文必需。 |

| 可用事实 | 含义 |
| --- | --- |
| ENDPOINT | 已得到唯一端点，不表示访问获准。 |
| ACCESS_GRANTED | 接入已放行，获准上下文存在。 |
| VERIFIED_CLIENT | 获准上下文实际携带已验证 Client。 |
| AUTHENTICATED_SUBJECT | 获准上下文实际携带已认证 Subject。 |

事实从本次上下文推导，不能独立伪造。匿名获准可没有可信 Client 或 Subject。可选事实缺失时对应能力/单元不适用；阶段必需事实缺失则失败，不能跳过安全能力继续转发。附加事实要求采用 AND；未声明表示无附加要求，不取代阶段不变量。

执行时机必须非空；单元及其身份、事实依赖不得缺失。unitId 在能力内非空白且跨槽位稳定；同名且载荷/事实依赖相等的单元可折叠，同名但不同内容必须拒绝，即使其键已执行或事实尚缺失。完整计划及执行支持必须在首个副作用之前校验。

ONCE_PER_REQUEST 的键为能力标识+单元标识，ONCE_PER_SLOT 另含槽位；不同能力的同名单元互不干扰。只有完成的批次记录执行键，拒绝也属完成；缺失事实、未匹配、取消或未执行单元不记录。执行键只证明参与去重，不证明副作用成功或转发指令已应用。

CONTINUE 继续普通能力链，STOP 停止普通链并处理该能力的具体产出；STOP 不必然是业务拒绝，也不免除资源清理。限流允许继续，超限/不可用停止；Header 与超时继续但其计划仍必须在转发前应用。一个批次产出可聚合多个单元，不要求逐单元一一对应。

## 6. Header 与信任上下文

HeaderConfig 持有可信字段选择、普通删除/设置及 CredentialForwarding；后者包括 authorizationHeader、cookies 和 allowedCookieNames。Client 签名凭据与转发设置是不同概念，端点不另持默认转发配置。

可信字段选择固定输出，普通字段允许受控设置/删除；Authorization 为 STRIP/PRESERVE，Cookie 为 STRIP_ALL/ALLOW_LIST。未命中 Header 配置时，默认选择全部受支持可信输出并全剥离凭据；显式选择较小集合时整体覆盖。未选择或来源缺失的可信字段不写入，也不能回退到请求声明。

普通名称须为合法 HTTP token，最长 128；值最长 4096，拒绝 CR/LF 等控制字符，允许空字符串。名称首尾空白非法，按小写规范化比较；规范化重名即使值相同也拒绝，跨操作冲突拒绝；值不修剪或改大小写。仅名称大小写不同的合法配置具有相同语义和规范化表示。

普通 set/remove 不得操作任何已登记共享 Header，部署配置不能放宽。部署否决按 set/remove 独立判定，名单外合法名称允许；不增加扩展准入名单或网关前缀准入。否决规则不进入业务快照或摘要；刷新规则前须先校验当前策略并整体切换，当前支持范围见职责文档。

| 固定可信输出 | 唯一来源 |
| --- | --- |
| APP_ID、APP_PLATFORM | VerifiedApp |
| CLIENT_ID | VerifiedClient |
| USER_ID、SESSION_ID、SUBJECT_TYPE | AuthenticatedSubject |

APP_VERSION 和签名字段不属于可选择的可信输出。可信字段不能重命名映射。客户端版本只作为声明，格式见 [PROTOCOL](PROTOCOL.md#证据大小与声明)；缺失、非法或不支持的声明不产生值，不证明身份。Trusted/Declared 表达来源，不是 Header 的永久可信分类。

转发前清除全部已登记共享 Header 与入站 X-GW- 前缀字段，再按计划写入。未验证 Client 声明不写入 CLIENT_ID。历史名称与其他未登记自定义名称同样处理，不提供别名、专门清除或兼容转换。共享名称所有权和下游信任要求见职责文档，名称表示见协议文档。

## 7. 快照与发布

**入口快照 `IngressSnapshot`** 由 `SnapshotVersion`、`IngressContent`（App/Client/Endpoint 注册表与 CapabilityPolicyConfig）、`contentHash` 与 `publishedAt` 构成，不可变。它服务整个入口管道。

- `contentHash` 标识内容，具体编码见 [协议文档](PROTOCOL.md#快照与规范化编码)。
- 一个请求只读取一次快照。

**快照自身不变量**：

- Client 引用的 App 必须已登记。
- active 与 grace 凭据都必须在 `publishedAt` 时刻处于有效期内。
- `credentialId` 在全部 Client 间唯一。
- `credentials` 为 null 的 Client 跳过凭据校验。
- 任何校验失败以 `ACCESS_SNAPSHOT_INVALID` 拒绝，配置错误不静默降级。

内容校验检查注册能力、配置类型及能力专属规则，适用于 Definition 保存、编解码和请求组装。完整校验还核对同一内容中的绑定引用（AppId、已验证 ClientId 必须存在）、受影响端点与同层竞争；发布及完整快照编码/解码必须执行，加载校验成功后才允许切换运行快照。快照自身不变量不能替代完整校验；摘要正确也不能替代有效性。

**Definition 保存与发布**：

- Definition 独立 CAS 保存；Publish 读取全部 revision、组装全量校验、再次确认 revision、CAS 发布。
- 无变化不增加版本。
- 加载先校验再原子切换，失败保留最后有效版本，首次无有效版本则启动失败。
- 未知必需能力拒绝加载。
- 派生索引不构成另一套运行版本。

**Client 接入设置与凭据设置**保留独立管理操作，但共用 ClientAccess Definition 和 `sourceRevision`：

- 修改接入设置保留原凭据；更新凭据保留 `ClientStatus`/`allowedApps`。
- 凭据更新要求 Client 已存在。
- 旧 revision 写入冲突，不允许两个操作互相覆盖。
- 共四份 Definition：App、Client、Endpoint、能力组合；端点接入状态并入端点注册表定义。

**发布与回退**以独立 revision/CAS 维持一致性：

- 回退恢复全部 Definition 后以更大版本发布。
- 永久 rollbackState 阻断普通保存/发布，支持幂等重入，全部完成才清除。
- 恢复通过显式重试完成，不定义独立领域事件或补偿调度。

**回退决策顺序**：

- 先验证目标存在、Header 部署准入和完整发布有效性，再于首次恢复写入前激活阻断。
- 发布前取一次业务时间校验凭据有效期；历史凭据已过期则在写入前拒绝。
- 前置失败不新增阻断，也不清除上次恢复遗留的阻断。
- 每份成功恢复的 Definition CAS 都是重试检查点；部分失败保持阻断。
- 目标内容已发布而清除失败时，重试沿用原发布时间校验，不重复增加版本或修改发布时间。
- 支持更换合法目标重试。

快照接纳统一判定：低版本 STALE 忽略；同版本同摘要 UNCHANGED 幂等接纳；同版本不同摘要 CONFLICT 拒绝；通过完整校验的高版本 ADVANCE 接纳。源修订令牌只用于 CAS，不替代发布版本。通知、切换和最后有效快照持久化机制见技术设计。

发布与回退返回完成后的 snapshotVersion、contentHash、publishedAt，不代表全部实例已切换。幂等回退复用已发布快照时不改变发布时间。

不同实例允许短暂相邻版本；不承诺全局瞬时撤销。令牌余额是执行状态，不作为快照数据；无关发布不重置状态。

## 8. 管理用例

管理编辑态 Definition 与发布态 Snapshot 分开；运行请求不查询最新编辑态。写入需可写存储，查询在只读部署仍有用例语义；外部端点及可达性限制见职责文档，载荷见协议文档。

sourceRevision 是 App、Client、Endpoint、能力组合各文件级的不透明令牌；snapshotVersion 是正数全局发布版本。二者不能相减或比较大小；已保存未发布通过组装内容和发布内容的规范化摘要比较。读取值及其修订令牌必须属于同一观察结果。

| 用例 | 前置条件与成功结果 | 失败与限制 |
| --- | --- | --- |
| 查询 Definition | 返回集合全量内容和共享源修订；未初始化的修订为空。凭据只提供编辑所需身份、协议及有效期。 | 不回显 secretRef。 |
| 保存 App/Client/Endpoint 内容 | 单条 upsert；创建及保留规则见第 2.1 节，成功返回新源修订。 | 保存不发布；不能借内容编辑切换状态或覆盖 Client 凭据。 |
| 状态切换 | 以身份和目标状态单独表达上线/下线。 | 与内容编辑分属不同授权与审计意图。 |
| Client 凭据轮换 | Client 必须已存在，整体替换 active/grace，省略 grace 即清除。 | 与接入编辑共享修订，轮换失败不改写其他接入内容。 |
| 保存能力组合 | 完整替换 Bundle/Binding，执行内容校验；两者均为空可清空编辑态。 | 跨 Definition 引用与同层竞争在发布时完整校验；不提供单 Bundle PATCH。 |
| 发布 | 读取全部 Definition、完整校验、重验源修订后 CAS；完成返回快照事实。 | 无变化不增加版本，冲突不覆盖；不代表全实例切换。 |
| 回退 | 目标为正数历史版本；恢复和阻断规则见第 7 节。 | 非法/过期目标在写入前拒绝；部分恢复保留阻断。 |
| 查询当前快照 | 返回发布态快照事实。 | 从未发布时字段为空。 |

保存命令须提供非空白且匹配最近读取/保存结果的 expectedSourceRevision；不提供空令牌初始化 Definition。保存先检查回退阻断、核对修订并执行 CAS；旧修订或 CAS 失败拒绝，不自动重试覆盖。相同文件编辑互相冲突，不同 Definition 修订独立。

不提供删除用例；下线走状态切换。物理清理与批量迁移由配置维护方整体重建 Definition，再显式发布；编辑态令牌随之变化。

## 9. 业务决策归属

App、Client、Endpoint 持有各自身份、状态与编辑不变量；接入评估持有第 2.8 节的跨身份判定。能力组合持有绑定优先级、合并、竞争与有效性；能力自身持有配额、超时和 Header 规则；快照持有完整内容一致性与版本接纳。

用例协调事实取得、认证协作、阶段顺序、配额执行及保存/发布/恢复步骤，并复用上述判定。报文解析、密码学运算、路径匹配语法和实际传输由对应技术能力执行，不另维护领域决策副本。模块、包、装配及适配设计见技术设计。

## 10. 业务错误与领域事件

本服务使用 EDGE 前缀 800。共享领域段 000–099；快照 100–149；端点 150–199；App 200–249；Client 250–299；签名验证 300–349；350–599 为未分配领域段。应用 600–699、自有技术 700–799、外部适配 800–899、保留 900–999。已发布编号、模板与语义不得改动或复用；共享段 001/002/003 已退役且空置。

| 本地码 | 业务错误名称 |
| --- | --- |
| 000 | DOMAIN_ENTITY_ID_INVALID |
| 100 / 101 / 102 / 103 | ACCESS_SNAPSHOT_NOT_FOUND / ACCESS_SNAPSHOT_ALREADY_EXISTS / ACCESS_SNAPSHOT_INVALID / ACCESS_SNAPSHOT_POLICY_INVALID |
| 150 / 151 / 152 | ENDPOINT_NOT_FOUND / ENDPOINT_ALREADY_EXISTS / ENDPOINT_AMBIGUOUS |
| 200 / 201 / 202 | APP_NOT_FOUND / APP_ALREADY_EXISTS / APP_AMBIGUOUS |
| 250 / 251 / 252 / 253 / 254 | CLIENT_NOT_FOUND / CLIENT_ALREADY_EXISTS / CLIENT_AMBIGUOUS / CLIENT_CREDENTIAL_INVALID / CLIENT_CREDENTIAL_ROTATION_INVALID |
| 300 | SIGNATURE_VERIFIER_NOT_FOUND |

102 表达完整快照非法，包括跨内容绑定引用和同层竞争；103 表达策略规则非法，包括重复配置身份、未登记能力、Header 准入、限流约束和超时范围；253 表达 validFrom 不早于 expiresAt；254 表达 active/grace 身份重复或协议不一致。其他错误按其命名的身份缺失、已存在或冲突表达；稳定消息模板见 [已发布领域错误定义](../gateway-domain/src/main/java/com/cloud/gateway/domain/common/DomainError.java)。

应用失败包括修订/发布冲突、认证失败、历史缺失、无变化、编辑态变化、锁不可用、回退阻断、只读和预算耗尽，稳定码与模板见 [已发布应用错误定义](../gateway-application/src/main/java/com/cloud/gateway/application/common/ApplicationError.java)。613 的超时结果不证明远端未提交，检查当前状态后重试。接入拒绝的十六种原因及判定顺序以第 2.8 节为准，不与业务异常码混为一套编号；HTTP 投影见职责文档。

内部参数、技术集合或注册类型的编程错误不占用业务错误含义，技术诊断归设计与适配契约。当前这些领域行为不定义独立领域事件；状态转换与发布事实不自动成为对外事件承诺。
