# gateway 领域设计 · v1

本文是 v1 技术方案的领域设计权威来源。职责与交付见 [RESPONSIBILITIES.md](RESPONSIBILITIES.md)，运行配置见 [README.md](../README.md)。

一次请求的生命周期分为两个宏观阶段：**阶段一入口准入**与**阶段二下游治理**。v1 交付聚焦阶段一；阶段二的能力语义在本篇保留为权威设计，实现与验收随后续版本交付。

## 1. 请求生命周期：两阶段与能力库

### 1.1 阶段一：入口准入（v1 交付）

决定放不放行、以什么身份放行、扣多少额度，全部发生在代理之前。三段固定管道：

1. **准备段（prepare）**：捕获单次快照与原始请求事实，取得技术路由候选，执行认证前 IP 限流。此段不依赖任何身份——未知路径与歧义候选同样受保护。
2. **接入段（access）**：主体认证与客户端签名验证协作，端点/App/Client/Subject 接入评估，产生接入决策与获准上下文。
3. **准入段（admit）**：授权及绑定解析出 EffectivePolicy，执行认证后限流，定稿 Header 准备（凭据清除计划与可信 Header 取值）。

### 1.2 阶段二：下游治理（语义权威，v1 暂不交付）

只作用于获准请求：缓存读取、熔断许可、超时熔断执行、Header 转发应用、缓存写入/受控降级、响应生命周期（超时、缓冲、取消、连接释放）。被拒绝的请求不产生任何下游动作；认证拒绝、网关限流、缓存命中不计入熔断统计——它们不是下游故障。

**交接物**：入口准入向下游治理移交获准上下文（IngressAccessGrantedContext）、EffectivePolicy（限流、超时、熔断、缓存、Header 各节点）、配额执行记录、Header 准备结果与捕获的原始证据。缓存键使用清除前的请求证据；Header 安全清除属转发必需项，随阶段一交付。

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
| policyBinding 解析 | 准入段 | 一 | 是（阶段二节点仅作为交接物携带） |
| rateLimit（post-auth 槽位，身份维度） | 准入段 | 一 | 是（EXACT） |
| headerPreparation | 准入段 | 一 | 是 |
| headerApply（凭据清除 + 可信写入） | 下游治理 | 二 | 是（安全必需） |
| responseCache | 下游治理 | 二 | 暂缓 |
| circuitBreaker | 下游治理 | 二 | 暂缓 |
| timeout | 下游治理 | 二 | 暂缓 |
| staleDegrade | 下游治理 | 二 | 暂缓 |

限流是一个能力、两个槽位：规则使用的维度决定落点（IP 维度 → pre-auth，身份维度 → post-auth），同一规则跨阶段最多执行一次。执行拓扑固定、不可由调用者指定：顺序编码安全不变量（缓存先于授权即绕过认证、Header 写入先于认证即透传伪造身份、IP 限流后置即失去未知路径保护），违反时故障静默；能力间存在数据依赖（认证后能力消费获准上下文），自由排序是幻觉；接入评估的子序同时是未认证调用方的探测 oracle。组合需求由数据层承接（Bundle/Binding，见第 3 节）。固定链实例不止一条：**入口链**（上述拓扑）与**管理链**（第 4.4 节），两者复用能力库但拓扑各自固定。

## 2. 统一语言与模型

| 模型 | 定义 |
| --- | --- |
| App / AppId | 已登记应用载体，拥有 status、platform、allowedOrigins。声明 App 不等于证明身份。 |
| VerifiedApp | 获准上下文携带的应用事实，仅含 appId 与非空白 platform。 |
| Client / ClientId | 接入主体，同时代表原业务渠道；不再保留 Channel 或额外映射。 |
| Credential / Credentials | 归属于 Client 的签名凭据值对象，内嵌于 ClientAccess；active/grace 表达密钥轮换。 |
| AuthenticatedSubject | HOST_SESSION 或 BROWSER_SESSION；subjectId、sessionId 和 authenticationExpiresAt 为认证结果。 |
| IngressAccessEvaluator | 依据入口访问快照及 Client 验证、主体认证结果评估接入规则，产生 IngressAccessDecision。 |
| IngressAccessDecision / IngressAccessDenyReason | 接入允许或拒绝的决策与拒绝原因；两种结局都携带请求所持快照版本。 |
| IngressAccessGrantedContext | 接入获准后的身份上下文，仅由 Allowed 携带，不是评估前的输入。 |
| EndpointRegistry | 不可变端点注册表；重复 endpointId 或重复 (method, pathPattern) 匹配器在构造时拒绝。 |
| Endpoint | endpointId、serviceId、routeId、method/path matchers、tags、EndpointStatus。 |
| IngressAccessContent / IngressAccessSnapshot | 快照内容（App/Client/Endpoint 三个注册表与 PolicyConfig）与唯一版本化的不可变入口快照。 |
| PolicyConfig（策略配置） | 同一快照内 Bundle 与 Binding 的完整配置，负责引用及竞争节点校验；Bundle 持有能力节点，Binding 按条件把它们绑定到端点。 |
| PolicyBundle / PolicyBinding | 策略配置的组成部分：Bundle 持有能力节点，Binding 按条件（serviceId/endpointId/tags + AppId/verifiedClientId/SubjectType）把它们绑定到端点。 |
| TimeoutPolicy / CircuitBreakerPolicy / CachePolicy / HeaderPolicy | 单值完整配置节点——每种能力在 Bundle 内恰好零或一份，随 Bundle 整体覆盖，无独立身份、无逐请求决策。 |
| RateLimitRule / RateLimitRules | 限流的多值可寻址规则：Rule 有独立身份（RateLimitRuleId）构成配额键、按维度落到 pre-auth/post-auth 槽位；Rules 是 Bundle 的完整限流节点（规则组 + Redis 故障策略）。 |

v1 聚焦入口访问控制：Bundle/Binding/Header 组合仍由 PolicyConfig 建模并在发布前校验，其快照承载随后续实现补齐。

serviceId 是逻辑服务归属，routeId 是逻辑交付路由，SCG route.id 直接作为技术实现。同一服务可以因前缀改写或转发目标不同拥有多条路由；治理策略不同不要求拆路由。端点匹配器以 (method, pathPattern) 精确字符串在注册表内唯一，不能任意选第一条。tags 是受管理的分类，不来自请求，可为空。

App 拥有 AppStatus（ENABLED/DISABLED）；platform 仅来自配置，是可修改的受管理属性，必须非空白并去除首尾空白；platform 更正不要求重新登记 App 或重建 Client 授权，保存只更新 Definition，显式发布后生效。allowedOrigins 是规范化完整 HTTP Origin：scheme 与 host 小写、默认端口省略，不接受通配符、path、userinfo、query 或 fragment，仅接受 http/https；用于 Browser Session 非安全请求校验，不自动开启 CORS。

ClientAccess 定义 allowedApps 与可选 Credentials；ClientStatus 位于 Client 聚合。Credentials 包含 active 与至多一个 grace 凭据，两者必须同一签名协议且 credentialId 不同；credentials 为 null 表示未配置凭据，该 Client 不能通过签名验证，也不参与快照凭据校验。凭据通过 secretRef 解析密钥，credentialId 在快照内唯一。请求声明只定位配置，验证并完成防重放后才形成已验证 Client 身份；未验证的声明不产生 VerifiedClient。一个 Client 可使用多个受允许的 App。

EndpointAccess 使用 EndpointStatus（ENABLED/DISABLED），与 AppStatus、ClientStatus 保持显式状态建模；enabled() 仅为领域便捷判断，不是配置字段。EndpointAccess 由四个字段构成：clientVerificationRequirement、subjectAuthenticationRequirement、requiredScopes、requiredAudiences，前两者必填，两个集合可空。v1 聚焦入口访问控制，凭据转发设置不再属于端点，由 HeaderPolicy 承载。

clientVerificationRequirement 使用 ClientVerificationRequirement（REQUIRED/OPTIONAL），是对验证结果的策略：

- REQUIRED：必须声明 Client，且 Client 验证结果必须为 Verified。
- OPTIONAL：允许不声明 Client；一旦声明，仍检查登记、状态及 App 授权。
- 无论端点要求如何，提交的验证结果一律按结果处理：Verified 必须与声明一致，否则 CLIENT_VERIFICATION_MISMATCH；未声明却携带验证结果同样 MISMATCH；Rejected 拒绝；Failed（验证依赖不可用）拒绝，绝不置空放行。
- 不再提供 FORBIDDEN、端点级验证模式（IGNORE/REQUEST_SIGNATURE）及"声明但不验签"组合；签名验证只能由受管理凭据的协议发起，不能由请求自由选择或失败后降级。

subjectAuthenticationRequirement 使用 SubjectAuthenticationRequirement（REQUIRED/OPTIONAL），是对认证结果的策略：

- REQUIRED：必须提供已认证主体。
- OPTIONAL：未提交 Token（Absent）匿名放行；提交的 Token 已过期（EXPIRED）有意视同未提交并放行；其余拒绝原因（INVALID）拒绝。
- 认证依赖不可用（UPSTREAM_UNAVAILABLE）与响应非法（RESPONSE_INVALID）一律拒绝，不能降级匿名或使用缓存身份。
- 端点不再声明 Host/Browser 会话类型；会话家族选择由 security 层 SubjectAuthenticationSelection 表达，v1 仅 HOST。管理入口最低要求（REQUIRED + admin scope + gateway audience）由 ManagementAccessRules 表达，Browser Session 不得成为管理主体。

requiredScopes、requiredAudiences 是端点附加的访问要求：null 归一为空集合，非空元素必须非空白，保存为不可变集合。配置非空时，主体必须包含全部指定值（子集 AND 校验）。Browser Session 不产生 OAuth2 scope/audience（集合恒为空），为其配置非空要求即不可满足。空集合不关闭 Token 认证阶段的 audience 校验，也不放宽管理入口的最低要求。

接入评估中，禁用与不存在具有相同业务语义：App、Client、Endpoint 分别返回对应的 NOT_FOUND 拒绝原因，不另设禁用拒绝原因。评估顺序固定为端点 → App → Client → Subject；只有本次端点规则实际接纳的验证/认证结果才能进入获准上下文。Browser Session 主体对非安全方法请求必须命中 App 的 allowedOrigins，否则 BROWSER_ORIGIN_NOT_ALLOWED；无主体时无法识别浏览器请求，不做 Origin 校验。

认证结局区分缺失（Absent）、认证成功（Authenticated）、拒绝（Rejected）、依赖不可用（Failed）；Client 验证结局同构。sessionId 重命名不改变既有会话标识语义。authenticationExpiresAt 仅保留在 AuthenticatedSubject 中描述认证到期信息，不是会话绝对期限或到期前不会撤销的保证，目前无后续治理消费者。

AuthenticatedSubject 的校验按 subjectType 分支执行：两种主体都要求 subjectType、非空白 subjectId/sessionId 及 authenticationExpiresAt；subjectId/sessionId 去除首尾空白。HOST_SESSION 要求非空白 issuer 并去除首尾空白，audiences/scopes 允许输入 null 或空集合，null 归一为空集合，已有元素必须非空白，集合不可变。BROWSER_SESSION 拒绝非空白 issuer 或非空 audiences/scopes；允许 null/空集合，最终将 issuer 归一为 null、集合归一为空集合。不能在分支前统一要求 audiences/scopes 非空。

IngressAccessGrantedContext 仅含三个字段：app（VerifiedApp，必填）、client（VerifiedClient，可 null）、subject（AuthenticatedSubject，可 null）。VerifiedClient 含 clientId、credentialId、verificationMode、verifiedAt，只表达已通过凭据验证的 Client；请求声明的 Client 只作为入口校验输入，不进入获准上下文。v1 决定直接复用完整 AuthenticatedSubject（保留 issuer 与 authenticationExpiresAt），不构造裁剪后的 SubjectContext 值对象；上下文的可信性来自认证及接入评估流程，字段完整不等于认证成功。快照版本属于请求所持快照，记录在 Decision 上，不放入身份上下文。

## 3. 策略组合与匹配

本组合是数据层组合机制，服务准入段的绑定解析与阶段二的能力节点；v1 保留模型与发布校验，阶段二节点仅作为交接物携带。

**Policy 与 Rule 的分界**：`*Policy` 后缀（Timeout/CircuitBreaker/Cache/Header）标记**单值完整配置节点**——每种能力在 Bundle 内恰好零或一份，被 Binding 整体覆盖，不产出逐请求决策；`RateLimitRule` 标记**可独立执行的可寻址单元**——有独立身份构成配额键、可累加、跨阶段去重。Policy 被覆盖，Rule 被执行。这一差异决定了两个能力族的合并语义不同（见第 4.1 节）。

PolicyBinding 通过 bundleId 引用同一快照内的 PolicyBundle。编辑 Bundle 不影响运行请求，显式发布后影响全部引用它的 Binding；运行时不查询最新编辑态。

绑定优先级为全局 < 服务 < 标签 < 端点，可附加 AppId、已验证 ClientId、SubjectType 条件；条件采用 AND，标签集合全部满足，上下文条件不额外提高优先级。没有任意脚本。超时、熔断、缓存和 Header 按完整节点覆盖，不做字段深合并。同层可同时命中且竞争同一节点的绑定拒绝发布；限流累加而非覆盖，同一 Bundle 重复引用只执行一次限流。发布校验引用、范围与受影响端点。空节点表示不声明该能力，不能用空值关闭继承能力；需要例外时应收窄上层绑定范围或调整受管理标签。

## 4. 阶段一：入口准入

### 4.1 准备段（prepare）

**入口**：application 的 `IngressPreparationCommandService.prepare` 是唯一概念入口，HTTP 中性、返回 CompletionStage；命令为 `PrepareIngressCommand`，单字段包装 `RequestEvidence`——命令就是"请携带这份证据"，不新增任何事实。协议入口是 interfaces 的准备段 WebFilter——运行链中唯一调用方（也是 `RequestEvidence` 的唯一生产方），位于安全链与 SCG 路由分发之前，每请求恰好执行一次（完成标记防重入）。

**入参（RequestEvidence，一次性捕获的完整请求事实）**：method（大写化）、rawPath/rawQuery（不解码不改写；null = 无查询，空串 = 空 query，二者对签名语义有区别）、声明 AppId/ClientId（可空）、Origin（可空）、remoteIp（限流用 IP，由 interfaces 按部署配置解析，v1 取 remote address，X-Forwarded-For 归部署侧）。`unsafe()` 由 method 派生——安全方法集为 GET/HEAD/OPTIONS/TRACE。签名证据材料**不进入准备段**：是否采集取决于匹配后的端点策略，1 MiB/413 规则属接入段签名验证。

**流程（六步）**：

1. **snapshotCapture**：读取当前快照，恰好一次。运行期不存在无快照状态——首次启动无有效快照已由启动失败保证，LKG 保证降级有界。
2. **endpointMatch**：路径语法适配器（`EndpointMatchSource` 端口，interfaces 以 SCG PathPattern 实现）产出候选，domain 依 `EndpointMatch` 归类为 UNIQUE/NONE/AMBIGUOUS。**路径语法是适配器事实，胜者选择是领域规则**：相同候选折叠为单一——端点被自己的多个匹配器命中时以 [E, E] 到达；同 id 但内容分歧、或两个不同端点命中，均为歧义。只有唯一候选贡献端点级保护，歧义与未知走全局规则，不向调用方泄露差异。
3. **rateLimit（pre-auth 槽位）**：`PolicyResolver.preAuthRules` 取全局与无身份条件绑定的限流规则组（与准入段 `resolve` 共用同一套绑定选择规则；null 端点 = 仅全局），`RateLimitEvaluator` 只执行 IP 维度规则，其余维度缺失跳过。

限流（两槽位共享语义）：v1 只交付 EXACT。Redis 原子令牌桶每次扣减共享额度。PREALLOCATED 为后续模式，不允许启用或静默替换。多条适用规则全部执行，相同维度不能只执行第一条。认证前 IP 保护覆盖未知路径；认证后按 App、已验证 Client、Subject、Endpoint 限流。公开 App 声明不能作为唯一防滥用维度。超限 429 + Retry-After；Redis 故障按策略 CLOSED（503）或 OPEN_WITH_LOCAL（有界本地配额、告警，不承诺集群精确）。稳定规则身份构成配额键，不追加 snapshotVersion 令每次发布重获满额。

RateLimitEvaluator 只决定适用规则和聚合结果：缺失／空白维度跳过，同一规则（按 RateLimitRuleId）跨阶段最多执行一次；不可用优先于超限，超限取最大等待时间，全部允许时取最小剩余额度。QuotaCommandService 执行全部适用规则并记录结果，按领域故障规则协调共享额度与本地应急额度；技术适配器不再决定降级模式。

**拒绝位置**：准备段只拒绝限流失败（429/503）；歧义与未知端点不在此拒绝——以 null 端点进入接入段，由接入评估统一 ENDPOINT_NOT_FOUND，不提供专门歧义原因，避免向调用方泄露路径歧义；歧义本身是配置缺陷，另计入低基数指标供部署侧告警，对内可见、对外统一。

**出口与交接**：输出为密封接口 `IngressPreparationOutput`，两种结局靠类型区分而非调用方自觉——`Prepared`（snapshot、endpointMatch、evidence、quota）与 `Denied`（聚合决策，请求在边缘终止为 429/503，不存在交接）。`quota` 是 `QuotaExecution`——**配额执行记录**：`Set<RateLimitRuleId> executedRuleIds` 与槽位聚合结果 `outcome` 一体化，`executed(ruleId)` 把跨槽位去重表达成意图——准入段解析出同一条规则时按此跳过，实现"同一规则跨阶段最多执行一次"。规则身份为值对象 `RateLimitRuleId`（与 AppId/ClientId 同款），不再使用裸 String。交接硬规则：接入段命令由 `Prepared` 构造，接口层不得重新采集事实或重读快照；SCG 路由核对发生在交接路径上、接入段之前（interfaces 职责）：唯一候选的 routeId 必须等于 SCG 实际命中路由 id，不一致拒绝。请求取消经 IngressTask 级联终止未完成的限流协作。

### 4.2 接入段（access）

评估语义（接入顺序、验证/认证结果策略、拒绝原因、获准上下文构成）见第 2 节。本段定义认证协作与签名协议。

**认证协作**：IngressAccessCommandService 负责认证协作与领域规则调用；应用阶段只使用请求事实、领域对象和 CompletionStage，不接收 Spring Security 或 Reactor 类型；请求取消及异步失败终止其未完成协作。

**签名**：历史协议计算保持不变：

- LEGACY_SECRET_PARAMS_TIMESTAMP_MD5_UPPER：MD5(secret + params + timestamp) 大写，保留原 params 提取方式。
- LEGACY_SECRET_TIMESTAMP_BODY_SHA256：SHA256(secret + timestamp + body)。
- LEGACY_SECRET_TIMESTAMP_BODY_HMAC_SHA256：HMAC-SHA256(secret, timestamp + body)。

HTTP_REQUEST_SIGNATURE_V1 是独立新协议，覆盖原始 method、路径/查询、AppId、ClientId、credentialId、timestamp、nonce、body 摘要，固定编码及规范化规则；在改写前验证。协议由受管理凭据指定，不能由请求自由选择或失败后降级。

签名请求及 GET 请求的入口 body 证据采集上限为 1 MiB，超限返回 413；有 body 的 GET 不缓存。普通未签名非 GET 请求不由该证据采集器统一缓冲。

新协议输入 Header 为 `X-App-Id`、`X-Client-Id`、`X-Credential-Id`、`X-Timestamp`、`X-Nonce`、`X-Signature`。签名原文依次编码：协议名 `HTTP_REQUEST_SIGNATURE_V1`、method、rawPath、rawQuery、AppId、ClientId、credentialId、timestamp、nonce、body 的 SHA-256 小写十六进制摘要。每个字段使用 UTF-8 字节长度的 netstring：`长度:值,`；缺失 query 使用 `-1:,`，空 query 使用 `0:,`。不解码、不排序、不重写原始路径/查询。timestamp 为规范十进制 epoch 毫秒；nonce 为 22–128 个 base64url 字符，由调用方用至少 128 bit 随机数生成。签名是以 secret 为密钥、上述字节为消息的 HMAC-SHA256，小写十六进制 64 字符。防重放按 Client+nonce 隔离，而非 credentialId，轮换不能绕过重放约束。

secret 只通过 secretRef 解析，不写入快照/日志。恒定时间比较；成功后原子登记防重放，登记失败不得建立身份。active/grace 只表示密钥轮换；新协议按 credentialId 精确选择有效候选，历史协议最多依序尝试两份。凭据归属 Client 的身份由调用方传入并与证据核对。协议迁移与密钥轮换分开。

凭据缺失或错误返回 401；密钥解析或防重放依赖不可用返回 503，不能转换为匿名或尝试旧协议兜底。

domain 定义签名原文、输入格式、候选选择、时间窗口和防重放身份；application 协调密钥读取、密码学端口和防重放登记。Redis 空响应属于依赖异常，不等同真实重复登记；请求取消向未完成的登记传播。

### 4.3 准入段（admit）

- **授权及绑定解析（policyBinding）**：以获准上下文与端点解析 EffectivePolicy；Header 默认继承在 domain 完成（默认凭据转发为全剥离，无端点级默认值）。阶段二能力节点（超时、熔断、缓存）仅作为交接物携带。
- **rateLimit（post-auth 槽位）**：App、已验证 Client、Subject、Endpoint 维度，共享语义见 4.1。
- **headerPreparation**：按命中的 HeaderPolicy 定稿凭据清除计划与可信 Header 取值；固定安全规则不可关闭：先删除所有受保护字段，再写验证结果；来源缺失则不写入。语义详见第 6 节。

段产物即两阶段交接物：获准上下文、EffectivePolicy、配额执行记录、Header 准备结果与捕获证据。

### 4.4 管理入口链

管理端点走第二条固定链：HOST_SESSION 主体 + admin scope + gateway audience 最低要求（ManagementAccessRules 在启动时对当前快照校验最低要求，运行期安全链独立执行同一领域规则）；Browser Session 不得成为管理主体。管理链不使用客户端签名验证、Browser Origin 校验与身份维度限流。

## 5. 阶段二：下游治理（语义权威，v1 暂不交付）

只作用于获准请求，输入为两阶段交接物。v1 仅交付其中的 headerApply（安全必需：凭据清除不可延迟），其余能力实现与验收随后续版本交付。

### 5.1 缓存

默认关闭，仅显式主体无关 GET；读取前仍认证及授权。键含缓存策略指纹、EndpointId、method、原始路径/查询和影响响应的 App/Client/语言等维度。主体相关内容不能忽略 Subject 直接共享。

private、no-store、Set-Cookie 响应不缓存；携带 Authorization/Cookie 默认不共享。Vary 必须支持，否则跳过缓存。限制总容量、单响应大小、TTL 和旧缓存窗口；不缓存 SSE/下载流。命中不续期。只有相关策略变化才隔离对应内容。

v1 保守限定为无请求体、已完成授权且无凭据、无 Subject 的 GET，响应为 200 JSON 或 text/plain；携带条件请求、Range 或响应要求重新验证时跳过缓存。实际缓存键分别对原始请求及受控转发 Header 做摘要隔离，Header 策略变化会隔离旧内容；trace/nonce 等变化也会降低命中率，不会漏掉未声明的响应维度。尊重响应 Age/max-age，并将完整读取耗时计入新鲜期；不提供后台刷新、请求合并或跨实例共享缓存。

### 5.2 超时、熔断与降级

区分连接、响应等待、总时限；连接超时必须由 HTTP 客户端实施。流式请求不走普通缓冲响应缓存。响应提交后不能改写标准错误体。

熔断状态实例本地，默认按 routeId 隔离，统计真实下游连接失败、超时、选定 5xx；认证拒绝、网关限流、缓存命中不计入。无关策略变化不得重置状态。熔断不等于并发隔离。

v1 的慢调用时长统计到下游响应 Header，故障状态选定 500/502/503/504；响应 Header 之后的写出失败或客户端取消不污染下游熔断统计。总时限仍覆盖写出，但已提交响应只能终止，不能再降级改写。熔断实例数量有界，容量淘汰后该实例的历史不保留。

只允许标准错误或明确只读端点的有限旧缓存降级；503 表示不可用，调用超时通常 504。不返回伪成功，不自动重试非幂等请求。认证/授权失败不得触发缓存降级。

### 5.3 Header 转发应用

headerApply 执行准备段定稿的计划：发送下游前清除所有受保护字段，再按当前策略写入可信结果；来源缺失则不写入（信任语义见第 6 节）。客户端版本仅清洗后声明，不参与授权。历史名称与其他未注册自定义名称一样处理。

## 6. Header 与信任上下文

CredentialForwarding 是凭据转发设置值对象，包含 authorizationHeader、cookies 和 allowedCookieNames，由 HeaderPolicy 引用。v1 取消端点级默认转发设置，HeaderPolicy 是唯一来源，未配置时默认全剥离。ClientAccess.credentials 仅表示签名凭据，两者不混用。

HeaderPolicy 包含 Set<RequestHeader> trustedHeaders、普通字段删除/受控设置、Authorization STRIP/PRESERVE、Cookie STRIP_ALL/ALLOW_LIST。默认删除凭据。HeaderPolicy 构造时验证 HTTP token、名称长度 128、值长度 4096、控制字符、大小写重复和跨操作冲突，允许空字符串值；普通 set/remove 目标不得是已登记的 RequestHeader 名称。

framework-core RequestHeader 只登记有限的共享自定义 Header（CONTEXT、SIGNATURE），不登记 Host、Content-Length 等标准协议字段，不设 LEGACY 分类或历史名称别名。字符串消费点使用 headerName()；fromName(name) 验证 HTTP token 后忽略大小写查找，不修剪非法空白；fromType(HeaderType) 派生不可变集合。gateway HeaderRules 独立校验配置名称并规范化，不能将 fromName 返回空误判为合法名称。

trustedHeaders 直接选择固定输出：APP_ID、APP_PLATFORM、CLIENT_ID、USER_ID、SESSION_ID、SUBJECT_TYPE，由 TrustedHeaderResolver 统一解析：APP_ID/APP_PLATFORM 来自 VerifiedApp，CLIENT_ID 只来自已验证的 VerifiedClient，USER_ID/SESSION_ID/SUBJECT_TYPE 来自 AuthenticatedSubject；写入其 headerName，禁止重命名映射。APP_VERSION 和 SIGNATURE 字段不可选择。framework 不依赖网关上下文。

校验名称、重复目标、CR/LF、长度和协议控制字段。未验证 Client 声明不写 CLIENT_ID。不提供历史名称识别、专门清除或兼容转换，历史名称与其他未注册自定义名称一样处理。动态更新只改变固定可信字段的选择和普通字段操作，不提供名称映射。

HeaderRules 为不可变部署规则，配置名称在构造时规范化为小写存储，非法 token 字符、超长名称及大小写不同的重复名称拒绝。名称准入为部署扩展名单 extraHeaders 与 X-Gateway- 前缀；已登记的 RequestHeader（身份与签名字段）永远不能作为普通 set/remove 目标，部署扩展名单也不能放宽。forbiddenSetHeaders/forbiddenRemoveHeaders 独立否决对应操作。规则不写入业务快照或其内容摘要。当前规则在启动期固定，HeaderPolicy 随快照动态更新；不通过异步修改 Properties 放宽运行请求，未来若支持规则刷新须先校验当前策略并整体切换。

## 7. 快照与发布

入口快照 IngressAccessSnapshot 由唯一 AccessSnapshotVersion、IngressAccessContent（App/Client/Endpoint 注册表）、contentHash 与 publishedAt 构成，不可变。contentHash 计算规范化业务内容，不含版本和 publishedAt。一个请求只读取一次快照。

快照构造即全量校验：Client 引用的 App 必须已登记；active 与 grace 凭据都必须在 publishedAt 时刻处于有效期内；credentialId 在全部 Client 间唯一；credentials 为 null 的 Client 跳过凭据校验。任何校验失败以 ACCESS_SNAPSHOT_INVALID 拒绝，配置错误不静默降级。

Bundle、Binding 与 Header 组合由 PolicyConfig 建模，发布前对照同一内容校验绑定引用（AppId、已验证 ClientId 必须存在）与同层竞争节点冲突。

Definition 独立 CAS 保存，Publish 读取全部 revision、组装全量校验、再次确认 revision、CAS 发布。无变化不增加版本。加载先校验再原子切换，失败保留最后有效版本，首次无有效版本则启动失败。未知必需能力拒绝加载。预编译索引只是派生实现，不建第二套运行时版本。

Client 接入设置与凭据设置保留独立管理操作，但共用 ClientAccess Definition 和 sourceRevision：修改接入设置保留原凭据，更新凭据保留 ClientStatus/allowedApps，凭据更新要求 Client 已存在。旧 revision 写入冲突，不允许两个操作互相覆盖。共四份 Definition：端点接入状态并入端点注册表定义，不再有独立 ClientCredentialPolicy 仓储。

发布与回退沿用固定锁顺序、独立 revision/CAS 正确性边界。回退恢复全部 Definition 后以更大版本发布；永久 rollbackState 阻断普通保存/发布，支持幂等重入，全部完成才清除。不新增领域事件/补偿调度。

回退按 rollback → publish 顺序取得锁，先验证目标存在、HeaderRules 和完整发布有效性，再于首次恢复写入前激活阻断。发布前取一次业务时间校验凭据有效期，历史凭据已过期则在写入前拒绝。前置失败不新增阻断，也不清除上次恢复遗留的阻断。每份成功恢复的 Definition CAS 都是重试检查点；部分失败保持阻断。目标内容已发布而清除失败时，重试沿用原发布时间校验，不重复增加版本或修改发布时间。支持更换合法目标重试。

快照接纳（版本/内容准入）的细分语义 v1 暂不约定；Nacos sourceRevision 只服务技术 CAS，不替代 snapshotVersion。通知、原子替换及 LKG 持久化由适配器负责。

AccessSnapshotCommandService 仅委派管理入口；AccessPublicationCommandService 与 AccessRollbackCommandService 分别编排发布和恢复，共用类型明确的 AccessDefinitions 与单一 AccessSnapshotPublication CAS 边界。保存用例通过 AccessDefinitionWriter 组合复用阻断／revision／CAS 校验，各命令独立表达业务更新，不使用继承式 CRUD 模板。

不同实例允许短暂相邻版本；不承诺全局瞬时撤销。熔断、缓存、令牌余额是执行状态，不作为快照数据；无关发布不重置状态。

## 8. 管理面接口

管理面分为 definition 与 snapshot 两组、各分 command 与 query 共四个门面（`api/facade/admin/{definition,snapshot}`）：命令门面仅在存在可写 Definition 存储时装配，查询门面在只读部署同样可用。运行时请求路径不经过管理门面。

**两个版本轴独立前进**：`sourceRevision` 是 Definition 文件级令牌——每份集合文件（App、Client、Endpoint）各一个，由内容摘要派生、不透明、随整份文件前进，不随单条目孤立存在；`snapshotVersion` 是全局发布版本。两者之差即"已保存未发布"的编辑状态。CAS 粒度是文件，命令粒度是意图，二者不同层：查询是文件级状态投影，保存是文件级 CAS 之上的单意图操作。

**Definition 查询**：`getApps`/`getClients`/`getEndpoints` 按集合返回全量内容与该集合共享的 sourceRevision；revision 为 null 表示该 Definition 尚未初始化。查询不回显 secretRef，凭据只回显 credentialId、协议与有效期供编辑参考。

**Definition 命令**（全部返回保存后的新 sourceRevision，供连续编辑免重查）：

- `saveApp`/`saveClient`/`saveEndpoint`：单条内容 upsert，不含 status。创建以 ENABLED 初始化，已有实体的 status 不被 save 改变——发布才是生效闸门，"预置配置、暂不发布"即预置工作流。saveClient 只动接入设置、绝不触碰凭据；saveClientCredential 整对轮换（grace 缺省即清除），要求 Client 已存在，凭据不随接入编辑往返。
- `setAppStatus`/`setClientStatus`/`setEndpointStatus`：唯一的上线/下线入口，载荷仅 id + status，与内容编辑分属不同授权与审计事件。
- 删除不提供：下线一律走 status；物理清理与批量迁移走 Nacos 侧整体重建 Definition（绕过 API，令牌随之变化，之后经发布生效）。
- `expectedSourceRevision`：不透明 CAS 令牌，null/空 = 断言该 Definition 尚不存在（创建）；否则必须与最近一次读取或保存返回的值一致。并发保存不加写锁：存储层 CAS 裁决，失败方重读重放，冲突可见优于互相排队。同一文件的编辑互相冲突——Client 的接入设置与凭据轮换共享文件因此互相覆盖受拒；不同集合互不影响。

**Snapshot 门面**：`release` 显式发布，不携带调用方令牌（发布内部读取全部 Definition、组装校验、再次确认 revision 后 CAS 发布）；`rollback` 回退到正数的历史版本；`get` 返回当前生效快照，从未发布时字段为 null。发布与回退的锁顺序与阻断语义见第 7 节。

## 9. 分层与模块职责

domain 持有模型与纯规则；application 编排用例与端口；infrastructure 实现安全/存储/缓存/配额；interfaces 处理 HTTP 生命周期；boot 装配。不建设动态插件或脚本平台。

阶段一服务对应：IngressPreparationCommandService 承载准备段（捕获单次快照、技术匹配候选与认证前 IP 配额）；IngressAccessCommandService 负责认证协作与领域规则调用；IngressAdmissionCommandService 汇合认证、调用接入判断并执行认证后配额。TrustedRequestContextGlobalFilter 必须消费准备阶段已捕获的快照、端点、配额记录和完成标记，不自行重读、补选端点或补空执行记录；接口层负责证据、实际 SCG 路由核对、异步桥接、Header 写入和响应；GatewayRequestAttributes 属于 interfaces。

阶段二服务（v1 暂缓）：DownstreamGovernanceRules 根据独立请求／响应事实决定缓存读取、捕获、存储、STALE_CACHE 降级和下游故障观察，解释受管理 streaming 标签。DownstreamGovernanceCoordinator 先查缓存，再申请熔断许可，返回命中／执行／降级结果；DownstreamExecution 管理单次执行，许可最多结算一次。DownstreamResponseLifecycle 负责 HTTP 超时、缓冲、取消及连接释放；GatewayResponseWriter 负责提交前恢复响应元数据。缓存适配器保留控制头解析、摘要键、容量及条目存储，复用领域资格规则而不重新定义它们。

## 10. 错误及破坏式更新

EDGE 前缀 800：000–099 共享领域；100–149 快照；150–199 端点注册；200–249 App；250–299 Client；300–599 未分配领域；600–699 应用；700–799 自有技术；800–899 外部适配；900–999 保留。已分配编号与语义不复用。

开发阶段允许直接更名 Java/API/配置及重建开发快照，不保留旧 schema 别名层；历史签名算法保留。
