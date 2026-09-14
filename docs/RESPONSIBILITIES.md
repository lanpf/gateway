# gateway 职责与交付 · v1

领域模型及 v1 技术方案见 [DOMAIN.md](DOMAIN.md)，运行说明见 [README.md](../README.md)。

## 职责边界

gateway 将外部不可信请求转换为受控入口请求。请求生命周期分为两个宏观阶段：**阶段一入口准入**（v1 交付）——快照捕获、端点匹配、限流（认证前 IP 槽位与认证后身份槽位）、App/Client/Subject 接入评估、客户端签名验证、Header 安全准备；**阶段二下游治理**（语义已在 DOMAIN.md 定义为权威设计，交付暂缓）——缓存、超时、熔断降级与 Header 转发应用。Client 直接承接原业务渠道身份，不增加渠道映射。

账户、登录会话生命周期、Token 签发/撤销、Role/Permission 归 user-auth；资源归属及最终业务授权归对应业务服务。gateway 不查询这些业务数据、不签发 Token、不把入口放行解释为最终授权。

下游服务不暴露公网；网络策略只允许 gateway 和授权内部工作负载。内部调用仍使用可信内网，不增加 workload identity/delegation。Header 自身不是跨不可信网络的身份凭据。

## 协作契约

- 外部 App/Client 标识使用 gateway v1 协议。App 声明只证明匹配到 ENABLED 配置；可信 ClientId 仅由有效凭据恢复。客户端版本是非可信声明。
- 有限的共享自定义 Header（CONTEXT/SIGNATURE）由 [framework-core RequestHeader](../../framework/docs/RESPONSIBILITIES.md) 枚举声明。gateway 管理名称扩展和禁止 set/remove 名单，TrustedHeaderResolver 仅按固定名称输出六种可信事实。HeaderPolicy 使用 trustedHeaders 集合，不支持重命名映射。API、存储和本地配置统一使用枚举选择。
- IngressAccessGrantedContext 携带 VerifiedApp（appId、platform）、可选的 VerifiedClient（clientId、credentialId、verificationMode、verifiedAt）与可选的完整 AuthenticatedSubject；v1 决定保留 issuer 与 authenticationExpiresAt，不构造裁剪值对象。未验证的 Client 声明不进入上下文；入口获准不替代下游最终业务授权。快照版本记录在 IngressAccessDecision 上。AuthenticatedSubject.authenticationExpiresAt 在 Host 路径来自已认证 Token exp，在 Browser 路径来自收到验证响应时刻加剩余 idle TTL，不是会话绝对期限。
- gateway.headers 在启动时生成不可变 HeaderRules；保存、发布、快照编码/加载均校验名称准入及操作限制。HeaderPolicy 随快照动态更新，但部署规则当前变更需重启；不提供独立规则热刷新。此限制避免运行请求混用未经联合校验的规则和策略。
- 未发布阶段直接破坏式更新，不注册历史 Header 名称、LEGACY 分类或兼容映射，也不专门清除历史名称。未验签 Client 声明不能转为可信 CLIENT_ID。user-auth/framework-starter 读取逻辑迁移仍暂缓，现有下游必须迁移后才能按新契约协作；本次不改动这两个工程。历史签名算法按既定需求保留，与 Header 历史名称无关。
- Host Session 使用 JWT/JWK 或 Reference Token introspection，统一恢复 subjectId/sessionId；验证 issuer、时间、audience、scope 和必需声明。两种技术路径不泄露到领域上下文。
- 端点的 requiredAudiences/requiredScopes 是可选的附加访问要求（子集 AND 校验），不替代认证阶段校验；管理入口最低要求为 REQUIRED 主体、admin scope 和 gateway audience。
- Browser Session 使用 user-auth-api 发布的 Facade 路径与载荷，经受管理 serviceId 的内部实例调用。sessionId 保持现有返回身份；user-auth 管理 idle TTL、绝对期限和父会话。gateway 不刷新 Cookie。
- Browser Session 无效返回 401，不可达 503，非法响应 502；不可降级为匿名、宿主或缓存身份。非安全请求严格验证 App.allowedOrigins。
- /api/user-auth/** 与 /admin/user-auth/** 路由剥离服务段；/oauth2/** 与 /.well-known/** 原样转发。/internal/** 不登记也不公开路由。
- 管理端点要求 HOST_SESSION、admin scope 和 gateway audience。OAuth2 协议端点可按显式策略保留 Authorization；Cookie 只按名称白名单转发。
- 历史签名算法继续可用；新接入采用 HTTP_REQUEST_SIGNATURE_V1。协议迁移不等于密钥轮换，不允许验签失败降级。

## 交付状态

v1 交付聚焦阶段一入口准入：三段式固定管道（准备/接入/准入）、IngressAccessSnapshot 快照与发布、管理面四门面协议（definition/snapshot × command/query）、限流双槽位（EXACT）与 Header 安全准备。Header 转发清除属安全必需，随阶段一交付。阶段二下游治理（缓存、超时策略、熔断、降级）语义已在 DOMAIN.md 第 5 节保留为权威设计，实现与验收随后续版本交付；既有下游治理代码与历史测试结果不再代表当前 v1 验收范围。

PREALLOCATED、动态插件、任意脚本、业务权限判定、全局瞬时撤销不属于本期能力。

管理端点最低访问要求在启动时对当前快照校验；运行期安全链继续独立执行同一领域最低授权规则。该启动校验不等同于后续每次发布的管理端点配置审查，也不替代运行期纵深保护。

## 验收边界

### 两阶段模型与管理面协议文档同步（2026-09-13）

本轮全面重写 DOMAIN.md（两阶段生命周期、三段式入口准入、能力库与固定拓扑、阶段二语义保留为权威设计）与本文件职责/交付表述；管理面接口定稿为四个门面（IngressAccessDefinition/Snapshot × Command/Query，content-only 保存与独立状态转换，文件级 sourceRevision CAS 语义）。

验证：`mvn test -pl gateway-domain` 通过 107 项测试（0 失败）；`mvn compile -pl gateway-api` 通过；`git diff --check` 通过。domain 内 policy/security 包的历史编译错误已修复（缺失 import、判重条件反转、HeaderRules 规范化存储、HeaderPolicy 空值校验顺序）。已知欠账：interfaces/application 尚未对齐新管理面协议（controller/default facade 引用旧类型，application 引用已移除的 content.policies/validateAppUpdatesFrom/admissionOf），reactor 整体编译未通过；文档描述目标设计，不代表在途适配已完成。

### 接入命名与规则文档同步（2026-09-10）

本轮仅同步已确认设计：IngressAccessGrantedContext 命名及可空子上下文、按主体类型校验认证信息、ClientVerificationMode 的 null/REQUEST_SIGNATURE 语义，以及 Host 端点可不配置 requiredAudiences。领域规则以 [DOMAIN.md](DOMAIN.md) 为准；不新增文档，不修改 Java、配置、测试或其他在途内容。

文档描述的是目标规则，不代表在途适配已完成。本轮只读检查时，EndpointPolicy 仍保留 Host requiredAudiences 非空限制；SubjectContext 尚未按主体类型完成 null 输入归一；ClientVerificationMode 的 API、配置及映射适配仍需同步（旧 IGNORE、必填约束和枚举转换）。本轮不修复这些代码，也不将历史编译或测试结果视为本轮规则已通过验收。

### App 平台更正（2026-09-10）

同一 AppId 的 platform 现允许受管理更正，替代下述历史验证中的平台不变约束；保存、发布、回退及快照接纳使用一致语义。发布后可信平台输出随新快照变化，依赖该字段的下游需评估影响；本次不修改下游或其他在途重构。

Java 17 下 `mvn -pl gateway-application -am compile -q` 及 `git diff --check` 通过。定向测试命令 `mvn -pl gateway-application -am test -Dtest=AppPolicyTest,GatewayPolicySnapshotTest,PolicyCommandServicesTest -Dsurefire.failIfNoSpecifiedTests=false -q` 在 domain 测试编译阶段被已有 ServiceId 类型适配错误阻断，未执行测试；未修改这些在途适配点。

### 本轮职责整改验证（2026-09-09）

以下命令均显式使用 `/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home` 的 Java 17：

- `mvn clean test -q`：391 项单元／模块契约测试通过，失败、错误、跳过均为 0；既有集成测试完成编译适配，但未执行。
- `mvn -pl gateway-boot -am package -DskipTests -q`：启动包构建通过，不代表真实依赖环境启动验收。
- `git diff --check` 通过；12 个模块 POM 无依赖循环；domain/application 无 Spring、Reactor 或外层实现 import；生产代码无 var，语法树检查缺失控制流大括号为 0。

回归覆盖当前时刻已过期的历史回退在写入前拒绝、发布成功后凭据过期仍能完成阻断清除、App 平台不变约束、限流全规则执行及统一聚合、未知／歧义路径 IP 保护、准备阶段绕过拒绝、认证及配额取消、缓存资格与 Vary、Header 校验、Facade 必需参数与生成映射。真实生产 SecurityWebFilterChain 在内存上下文执行，覆盖 JWT／Reference 两种验证适配下的管理 401／403／放行及非 Host 证据选择；没有真实端口或中间件。

本轮未运行真实基础设施冒烟，暂缓的下游 Header 迁移及完整协作验收状态不变。

### 前轮开发验证（2026-09-09，职责复查前）

显式 Java 17：

- `mvn -pl gateway-application,gateway-infrastructure-coordination-redis -am test -q` 通过。
- `mvn clean test -q`：307 项单元／模块契约测试通过，失败、错误、跳过均为 0。
- `mvn -pl gateway-boot -am package -DskipTests -q`：启动包构建通过；此命令不执行测试。
- 静态检查：生产代码无 var、控制流缺失大括号为 0，domain/application 无 Spring/Reactor 或外层实现 import，`git diff --check` 通过。

新增验证覆盖回退前置失败和原有阻断保留、单快照认证及维度解析、延迟认证／取消传播、熔断许可最多结算一次、缓存捕获与降级重新检查。已有序列化固定向量、Header 准入及签名测试继续通过。开发阶段未运行 smoke；既有集成测试只做类型／构造器编译适配，不视为本轮真实基础设施验收。

### 历史冒烟与未完成协作验收

2026-09-08 最近一次 GatewayPipelineIT 的 5 项真实请求链测试通过；这是历史结果，本轮未重跑。历史完整协作场景最近结果为 27 项通过、1 项失败：BrowserSessionIngressIT 的现有 user-auth 运行包在 `/api/challenges` 未按新 Header 契约恢复 clientAppId/channelCode，必填校验失败。下游 Header 消费迁移仍按用户决定暂缓，未恢复旧 Header 别名，完整协作验收尚未通过。

上述历史结果不替代本轮 smoke。进入冒烟阶段后需复验真实请求链、Nacos 发布／回退和 Redis 协调，再单独评估暂缓的 Browser Session 协作。

- 外部不能绕过入口；未知端点和实际路由不一致拒绝。
- 伪造身份 Header 清除；未验证 Client 不能匹配可信身份绑定。
- Bundle 修改仅发布后生效；一个请求只用一个快照；非法配置保留 LKG。
- 配额按稳定规则执行，热更新不重获满额；Redis 故障语义可验证。
- 下游熔断只统计下游故障，超时与响应提交语义正确。
- 缓存先授权，隔离响应变化维度，拒绝敏感/流式缓存。
- 历史签名固定向量不变，新签名覆盖字段篡改和跨实例重放被拒绝。
- 日志不包含 Token、Cookie、secret 或完整载荷。
