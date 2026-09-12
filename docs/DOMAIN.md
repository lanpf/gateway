# gateway 领域设计 · v1

本文是 v1 技术方案的领域设计权威来源。职责与协作见 [RESPONSIBILITIES.md](RESPONSIBILITIES.md)，运行配置见 [README.md](../README.md)。

## 1. 统一语言与模型

| 模型 | 定义 |
| --- | --- |
| App / AppId | 已登记应用载体，拥有 status、platform、allowedOrigins。声明 App 不等于证明身份。 |
| Client / ClientId | 接入主体，同时代表原业务渠道；不再保留 Channel 或额外映射。 |
| ClientCredential | 归属于 Client 的签名凭据；active/grace 表达密钥轮换。 |
| AuthenticatedSubject | HOST_SESSION 或 BROWSER_SESSION；subjectId、sessionId 和 authenticationExpiresAt 为认证结果。 |
| IngressAccessEvaluator | 依据请求捕获的访问控制快照及认证结果评估入口接入规则，产生 IngressAccessDecision。 |
| IngressAccessDecision / IngressAccessDenyReason | 接入允许或拒绝的决策，以及拒绝原因。 |
| IngressAccessGrantedContext | 接入获准后的身份上下文，由 IngressAccessDecision.Allowed 携带，不是评估前的输入。 |
| EndpointRegistry | 不可变端点注册表。 |
| EndpointDefinition | endpointId、serviceId、routeId、method/path matchers、tags。 |
| PolicyBundle | 由 bundleId 标识、可以部分配置的类型化策略组合。 |
| PolicyBinding | 将服务、端点、tags 和调用上下文匹配条件绑定到 bundleId。 |
| PolicyComposition | 同一快照内 Bundle 与 Binding 的完整组合，负责引用及竞争节点校验。 |
| HeaderPolicy | 固定可信输出集合、普通字段处理和凭据转发策略。 |
| GatewayPolicySnapshot | 完整、不可变、唯一版本化发布单元。 |

serviceId 是逻辑服务归属，routeId 是逻辑交付路由，SCG route.id 直接作为技术实现。同一服务可以因前缀改写或转发目标不同拥有多条路由；治理策略不同不要求拆路由。端点必须唯一匹配，不能任意选第一条。tags 是受管理的分类，不来自请求，可为空。

AppPolicy 必须 ENABLED；platform 仅来自配置，是可修改的受管理属性，必须非空白并去除首尾空白。allowedOrigins 是规范化完整 HTTP Origin，不接受通配符、path 或 userinfo；用于 Browser Session 非安全请求校验，不自动开启 CORS。

同一 AppId 可以更正 platform，无需重新登记 App 或重建 Client 授权。保存只更新 Definition，显式发布后生效；在途请求继续使用已捕获的原快照。发布、回退及新快照接纳不因 platform 变化而拒绝，AppId 一致性及其他完整性校验保持不变。

ClientPolicy 统一定义 ClientId、allowedAppIds、ClientStatus 和可选的 ClientCredentials。ClientCredentials 是内嵌值对象，不再是独立策略，包含 active 与至多一个 grace 凭据；未配置时不能通过签名认证。凭据归属与 ClientId 必须一致，credentialId 在快照内唯一，active/grace 使用同一签名协议。请求声明只定位配置，验证并完成防重放后才形成已认证 Client 身份；未要求签名的已登记 Client 声明不得冒充 VERIFIED_CLIENT_ID。一个 Client 可使用多个受允许的 App。

EndpointPolicy 使用 EndpointStatus（ENABLED/DISABLED），与 AppStatus、ClientStatus 保持显式状态建模；enabled() 仅为领域便捷判断，不是配置字段。endpointId、clientRequirement、subjectAuthencationRequirement、credentialForwarding、status 必填。未登记、禁用或缺少访问策略默认拒绝，基础访问要求不能由治理绑定放宽。

clientRequirement 使用 ClientRequirement（REQUIRED/OPTIONAL/FORBIDDEN），控制是否允许或要求声明 Client；clientVerificationMode 使用 ClientVerificationMode，控制是否验证 Client。模式仅定义 REQUEST_SIGNATURE，null 表示不验证，不再使用 IGNORE 枚举值。两者的合法组合如下：

| clientRequirement | clientVerificationMode = null | REQUEST_SIGNATURE |
| --- | --- | --- |
| REQUIRED | 必须声明 Client，检查登记、状态及 App 授权，不验签 | 必须声明 Client 并通过请求签名验证 |
| OPTIONAL | 可以不声明；声明后仍检查登记、状态及 App 授权 | 非法组合 |
| FORBIDDEN | 禁止声明 Client | 非法组合 |

REQUIRED 本身不证明 Client 身份；未验证的声明不能产生 ClientContext。要求签名时，凭据缺失、验证失败或依赖不可用均不得通过置空 ClientContext 放行。

接入评估中，禁用与不存在具有相同业务语义，App、Client、Endpoint 分别返回对应的 NOT_FOUND 原因，不另设禁用拒绝原因。未配置 Client 验证时忽略传入的验证结果，获准上下文不携带 Client 身份；SubjectRequirement.IGNORE 同样不检查或携带传入的 Subject 身份。只有本次端点规则实际接纳的认证结果才能进入获准上下文。

SubjectRequirement 保留 IGNORE、HOST_SESSION_REQUIRED、HOST_SESSION_OPTIONAL、BROWSER_SESSION_REQUIRED。可选主体只允许缺少 Token 时匿名，提交非法 Token 必须拒绝。管理入口必须为 HOST_SESSION_REQUIRED + admin scope + gateway audience。Browser Session 不得变成宿主或管理主体。scope/audience 均采用子集 AND 校验；Browser Session 不产生 OAuth2 scope/audience。

requiredScopes、requiredAudiences 都是端点附加的访问要求：null 归一为空集合，非空元素必须非空白，并保存为不可变集合。Host 端点可以不配置任何附加要求，不能强制所有 Host 端点填写 requiredAudiences；非 Host 端点的这两个集合必须为空。配置非空时，主体必须包含全部指定值。空集合不关闭 Token 认证阶段的 audience 校验，也不放宽管理入口的最低 audience/scope 要求。HOST_SESSION_OPTIONAL 未提交 Token 时允许匿名；提交 Token 后必须通过认证及配置的附加要求。

sessionId 重命名不改变既有会话标识语义。认证结局区分缺失、认证成功、拒绝、依赖不可用；依赖不可用不能降级匿名或使用缓存身份。authenticationExpiresAt 仅保留在 AuthenticatedSubject 中描述认证到期信息，不是会话绝对期限或到期前不会撤销的保证，目前无后续治理消费者。

AuthenticatedSubject 的校验按 subjectType 分支执行：两种主体都要求 subjectType、非空白 subjectId/sessionId 及 authenticationExpiresAt；subjectId/sessionId 去除首尾空白。HOST_SESSION 要求非空白 issuer 并去除首尾空白，audiences/scopes 允许输入 null 或空集合，null 归一为空集合，已有元素必须非空白，集合不可变。BROWSER_SESSION 拒绝非空白 issuer 或非空 audiences/scopes；允许 null/空集合，最终将 issuer 归一为 null、集合归一为空集合。不能在分支前统一要求 audiences/scopes 非空。

IngressAccessGrantedContext 取代 TrustedRequestContext，仅包含 appContext、clientContext、subjectContext 三个字段，不采用中间候选名称 IngressIdentityContext 或 IngressAccessContext。AppContext 必填，表达通过登记与接入规则校验的应用，不等同凭据认证；其 appId 和非空白 platform 必填。ClientContext 可以为 null；存在时只表达已通过凭据验证的 Client，clientId、credentialId 均必填。请求声明 Client 只作为入口校验输入，不进入获准上下文，不以内部字段全空的对象代替缺失。

SubjectContext 可以为 null；存在时必须来自已认证主体，subjectType、非空白 subjectId/sessionId 必填，包含 audiences/scopes，但不复制 issuer 或 authenticationExpiresAt。集合约束跟随主体类型：Host 允许 null 输入并归一为空集合，已有元素非空白；Browser 只允许 null/空集合并归一为空集合；最终集合不可变。字段完整不等于认证成功，上下文的可信性来自认证及接入评估流程。快照版本属于请求所持快照，不放入身份上下文。

## 2. 策略组合与匹配

PolicyBinding 通过 bundleId 引用同一快照内的 PolicyBundle。编辑 Bundle 不影响运行请求，显式发布后影响全部引用它的 Binding；运行时不查询最新编辑态。

绑定优先级为全局 < 服务 < 标签 < 端点，可附加 AppId、已验证 ClientId、SubjectType 条件；条件采用 AND，标签集合全部满足，上下文条件不额外提高优先级。没有任意脚本。超时、熔断、缓存和 Header 按完整节点覆盖，不做字段深合并。同层可同时命中且竞争同一节点的绑定拒绝发布；限流累加而非覆盖，同一 Bundle 重复引用只执行一次限流。发布校验引用、范围与受影响端点。空节点表示不声明该能力，不能用空值关闭继承能力；需要例外时应收窄上层绑定范围或调整受管理标签。

## 3. 五项能力

### 限流

v1 只交付 EXACT。Redis 原子令牌桶每次扣减共享额度。PREALLOCATED 为后续模式，不允许启用或静默替换。多条适用规则全部执行，相同维度不能只执行第一条。

认证前 IP 保护覆盖未知路径；认证后按 App、已验证 Client、Subject、Endpoint 限流。公开 App 声明不能作为唯一防滥用维度。超限 429 + Retry-After；Redis 故障按策略 CLOSED（503）或 OPEN_WITH_LOCAL（有界本地配额、告警，不承诺集群精确）。稳定规则身份构成配额键，不追加 snapshotVersion 令每次发布重获满额。

RateLimitEvaluator 只决定适用规则和聚合结果：缺失／空白维度跳过，同一规则跨阶段最多执行一次；不可用优先于超限，超限取最大等待时间，全部允许时取最小剩余额度。QuotaCommandService 执行全部适用规则并记录结果，按领域故障规则协调共享额度与本地应急额度；技术适配器不再决定降级模式。

### 超时、熔断与降级

区分连接、响应等待、总时限；连接超时必须由 HTTP 客户端实施。流式请求不走普通缓冲响应缓存。响应提交后不能改写标准错误体。

熔断状态实例本地，默认按 routeId 隔离，统计真实下游连接失败、超时、选定 5xx；认证拒绝、网关限流、缓存命中不计入。无关策略变化不得重置状态。熔断不等于并发隔离。

v1 的慢调用时长统计到下游响应 Header，故障状态选定 500/502/503/504；响应 Header 之后的写出失败或客户端取消不污染下游熔断统计。总时限仍覆盖写出，但已提交响应只能终止，不能再降级改写。熔断实例数量有界，容量淘汰后该实例的历史不保留。

只允许标准错误或明确只读端点的有限旧缓存降级；503 表示不可用，调用超时通常 504。不返回伪成功，不自动重试非幂等请求。认证/授权失败不得触发缓存降级。

### 缓存

默认关闭，仅显式主体无关 GET；读取前仍认证及授权。键含缓存策略指纹、EndpointId、method、原始路径/查询和影响响应的 App/Client/语言等维度。主体相关内容不能忽略 Subject 直接共享。

private、no-store、Set-Cookie 响应不缓存；携带 Authorization/Cookie 默认不共享。Vary 必须支持，否则跳过缓存。限制总容量、单响应大小、TTL 和旧缓存窗口；不缓存 SSE/下载流。命中不续期。只有相关策略变化才隔离对应内容。

v1 保守限定为无请求体、已完成授权且无凭据、无 Subject 的 GET，响应为 200 JSON 或 text/plain；携带条件请求、Range 或响应要求重新验证时跳过缓存。实际缓存键分别对原始请求及受控转发 Header 做摘要隔离，Header 策略变化会隔离旧内容；trace/nonce 等变化也会降低命中率，不会漏掉未声明的响应维度。尊重响应 Age/max-age，并将完整读取耗时计入新鲜期；不提供后台刷新、请求合并或跨实例共享缓存。

### 签名

历史协议计算保持不变：

- LEGACY_SECRET_PARAMS_TIMESTAMP_MD5_UPPER：MD5(secret + params + timestamp) 大写，保留原 params 提取方式。
- LEGACY_SECRET_TIMESTAMP_BODY_SHA256：SHA256(secret + timestamp + body)。
- LEGACY_SECRET_TIMESTAMP_BODY_HMAC_SHA256：HMAC-SHA256(secret, timestamp + body)。

HTTP_REQUEST_SIGNATURE_V1 是独立新协议，覆盖原始 method、路径/查询、AppId、ClientId、credentialId、timestamp、nonce、body 摘要，固定编码及规范化规则；在改写前验证。协议由受管理凭据指定，不能由请求自由选择或失败后降级。

签名请求及 GET 请求的入口 body 证据采集上限为 1 MiB，超限返回 413；有 body 的 GET 不缓存。普通未签名非 GET 请求不由该证据采集器统一缓冲。

新协议输入 Header 为 `X-App-Id`、`X-Client-Id`、`X-Credential-Id`、`X-Timestamp`、`X-Nonce`、`X-Signature`。签名原文依次编码：协议名 `HTTP_REQUEST_SIGNATURE_V1`、method、rawPath、rawQuery、AppId、ClientId、credentialId、timestamp、nonce、body 的 SHA-256 小写十六进制摘要。每个字段使用 UTF-8 字节长度的 netstring：`长度:值,`；缺失 query 使用 `-1:,`，空 query 使用 `0:,`。不解码、不排序、不重写原始路径/查询。timestamp 为规范十进制 epoch 毫秒；nonce 为 22–128 个 base64url 字符，由调用方用至少 128 bit 随机数生成。签名是以 secret 为密钥、上述字节为消息的 HMAC-SHA256，小写十六进制 64 字符。防重放按 Client+nonce 隔离，而非 credentialId，轮换不能绕过重放约束。

secret 只通过 secretRef 解析，不写入快照/日志。恒定时间比较；成功后原子登记防重放，登记失败不得建立身份。active/grace 只表示密钥轮换；新协议按 credentialId 精确选择有效候选，历史协议最多依序尝试两份。协议迁移与密钥轮换分开。

凭据缺失或错误返回 401；密钥解析或防重放依赖不可用返回 503，不能转换为匿名或尝试旧协议兜底。

domain 定义签名原文、输入格式、候选选择、时间窗口和防重放身份；application 协调密钥读取、密码学端口和防重放登记。Redis 空响应属于依赖异常，不等同真实重复登记；请求取消向未完成的登记传播。

## 4. Header 与信任上下文

CredentialForwarding 是凭据转发设置值对象，不是独立策略；EndpointPolicy 和 HeaderPolicy 均以 credentialForwarding 字段引用它，包含 authorizationHeader、cookies 和 allowedCookieNames。EndpointPolicy 提供端点默认值，命中的 HeaderPolicy 提供整组覆盖值，行为不变。ClientPolicy.credentials 则仅表示签名凭据，两者不混用。

HeaderPolicy 包含 Set<RequestHeader> trustedHeaders、普通字段删除/受控设置、Authorization STRIP/PRESERVE、Cookie STRIP_ALL/ALLOW_LIST。默认删除凭据。固定安全规则不可关闭：先删除所有受保护字段，再写验证结果；来源缺失则不写入；普通规则不得覆盖身份或签名字段。

framework-core RequestHeader 只登记有限的共享自定义 Header（CONTEXT、SIGNATURE），不登记 Host、Content-Length 等标准协议字段，不设 LEGACY 分类或历史名称别名。字符串消费点使用 headerName()；fromName(name) 验证 HTTP token 后忽略大小写查找，不修剪非法空白；fromType(HeaderType) 派生不可变集合。gateway HeaderRules 独立校验配置名称并规范化，不能将 fromName 返回空误判为合法名称。

trustedHeaders 直接选择固定输出：APP_ID、APP_PLATFORM、CLIENT_ID、USER_ID、SESSION_ID、SUBJECT_TYPE，由 TrustedHeaderResolver 统一解析，写入其 headerName，禁止重命名映射。CLIENT_ID 只来自已验证 ClientContext；APP_VERSION 和 SIGNATURE 字段不可选择。framework 不依赖网关上下文。

校验名称、重复目标、CR/LF、长度和协议控制字段。发送下游前清除所有 CONTEXT、SIGNATURE 及自定义可信字段，然后按当前策略写入可信结果；来源缺失则不写入。未认证 Client 声明不写 CLIENT_ID。客户端版本仅清洗后声明，不参与授权。不提供历史名称识别、专门清除或兼容转换，历史名称与其他未注册自定义名称一样处理。动态更新只改变固定可信字段的选择和普通字段操作，不提供名称映射。

HeaderPolicy 构造时验证 HTTP token、名称长度 128、值长度 4096、控制字符、大小写重复和跨操作冲突；允许空字符串值。HeaderRules 的名称准入为 RequestHeader + extraHeaders + X-Gateway- 前缀，再独立判断 forbiddenSetHeaders/forbiddenRemoveHeaders。已登记身份和签名字段永远不能普通 set/remove，部署扩展名单也不能放宽。前缀只授予名称准入，不授予身份语义；允许受控普通设置。转发前清除整个前缀，再按当前策略设置，外部无法保留未配置的网关字段。名称准入只约束策略配置，不拒绝请求中其他标准 Header。

HeaderRules 为不可变部署规则，保存、发布及快照编码/加载时全量校验 Header 节点；规则不写入业务快照或其内容摘要。当前规则在启动期固定，HeaderPolicy 随快照动态更新；不通过异步修改 Properties 放宽运行请求，未来若支持规则刷新须先校验当前策略并整体切换。

## 5. 快照与发布

EndpointRegistry、App/Client/凭据/端点策略、Bundle、Binding、Header 一起发布。唯一 snapshotVersion；contentHash 计算规范化业务内容，不含版本和 publishedAt。一个请求只读取一次快照。

Definition 独立 CAS 保存，Publish 读取全部 revision、组装全量校验、再次确认 revision、CAS 发布。无变化不增加版本。加载先校验再原子切换，失败保留最后有效版本，首次无有效版本则启动失败。未知必需能力拒绝加载。预编译索引只是派生实现，不建第二套运行时版本。

快照接纳由领域统一区分旧版本、同版本同内容、同版本冲突和新版本。Nacos sourceRevision 只服务技术 CAS，不替代 snapshotVersion；通知、原子替换及 LKG 持久化仍由适配器负责。

Client 接入设置与凭据设置保留独立管理操作，但共用 ClientPolicy Definition 和 sourceRevision：修改接入设置保留原凭据，更新凭据保留 ClientStatus/allowedAppIds，凭据更新要求 Client 已存在。旧 revision 写入冲突，不允许两个操作互相覆盖；发布与回退均整体处理嵌套凭据。共五份 Definition，不再有独立 ClientCredentialPolicy 仓储。

发布与回退沿用固定锁顺序、独立 revision/CAS 正确性边界。回退恢复全部 Definition 后以更大版本发布；永久 rollbackState 阻断普通保存/发布，支持幂等重入，全部完成才清除。不新增领域事件/补偿调度。

回退按 rollback → publish 顺序取得锁，先验证目标存在、HeaderRules、App 身份连续性和完整发布有效性，再于首次恢复写入前激活阻断。需要新发布时取得一次业务时间，历史凭据在此时已过期则在写入前拒绝。前置失败不新增阻断，也不清除上次恢复遗留的阻断。每份成功恢复的 Definition CAS 都是重试检查点；部分失败保持阻断。目标内容已发布而清除失败时，重试沿用原发布时间校验，不因凭据后来过期而卡住收尾，不重复增加版本或修改发布时间。支持更换合法目标重试。

PolicySnapshotCommandService 仅委派管理入口；PolicyPublicationCommandService 与 PolicyRollbackCommandService 分别编排发布和恢复，共用类型明确的 PolicyDefinitionAccess 与单一 PolicySnapshotPublication CAS 边界。保存用例通过 PolicyDefinitionWriter 组合复用阻断／revision／CAS 校验，各命令独立表达业务更新，不使用继承式 CRUD 模板。

不同实例允许短暂相邻版本；不承诺全局瞬时撤销。熔断、缓存、令牌余额是执行状态，不作为快照数据；无关发布不重置状态。

## 6. 执行与分层

固定顺序：捕获单次快照与原始证据 → IP 保护 → 端点/路由检查 → App/Client/Subject 验证 → 授权及绑定 → 配额 → 准备凭据清除/可信 Header → 缓存 → 超时熔断下游 → 缓存写入/受控降级。缓存隔离仍使用清除前的请求证据。入口计时覆盖提前结束，指标不带 App/Client/Subject、路径或其他高基数敏感标签。

domain 持有模型与纯规则；application 编排用例与端口；infrastructure 实现安全/存储/缓存/配额；interfaces 处理 HTTP 生命周期；boot 装配。不建设动态插件或脚本平台。

IngressPreparationCommandService 捕获单次快照、取得技术匹配候选并执行认证前 IP 配额，EndpointRegistry 决定唯一性。未知或歧义候选只参与全局前置保护；配额允许后才拒绝歧义，不能因路径歧义跳过 IP 保护。IngressAdmissionCommandService 汇合认证、调用接入判断并执行认证后配额；IngressAccessCommandService 负责认证协作与领域规则调用。策略解析在 domain 完成 Header 默认继承。应用阶段只使用请求事实、领域对象和 CompletionStage，不接收 Spring Security 或 Reactor 类型；请求取消及异步失败终止其未完成协作。

TrustedRequestContextGlobalFilter 必须消费准备阶段已捕获的快照、端点、配额记录和完成标记，不自行重读、补选端点或补空执行记录。接口层负责证据、实际 SCG 路由核对、异步桥接、Header 写入和响应；GatewayRequestAttributes 属于 interfaces。管理入口安全链使用领域认证选择与管理授权规则，不能因迁移职责而移除纵深保护。

DownstreamGovernanceRules 根据独立请求／响应事实决定缓存读取、捕获、存储、STALE_CACHE 降级和下游故障观察，解释受管理 streaming 标签。DownstreamGovernanceCoordinator 先查缓存，再申请熔断许可，返回命中／执行／降级结果；DownstreamExecution 管理单次执行，许可最多结算一次。DownstreamResponseLifecycle 负责 HTTP 超时、缓冲、取消及连接释放；GatewayResponseWriter 负责提交前恢复响应元数据。缓存适配器保留控制头解析、摘要键、容量及条目存储，复用领域资格规则而不重新定义它们。

## 7. 错误及破坏式更新

EDGE 前缀 800：000–099 共享领域；100–149 快照；150–199 端点注册；200–599 未分配领域；600–699 应用；700–799 自有技术；800–899 外部适配；900–999 保留。已分配编号与语义不复用。

开发阶段允许直接更名 Java/API/配置及重建开发快照，不保留旧 schema 别名层；历史签名算法保留。
