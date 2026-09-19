# gateway 职责与交付 · v1

领域模型及 v1 技术方案见 [DOMAIN.md](DOMAIN.md)，运行说明见 [README.md](../README.md)。

## 职责边界

gateway 将外部不可信请求转换为受控入口请求。请求生命周期分为两个宏观阶段：**阶段一入口准入**（v1 交付）——快照捕获、端点匹配、限流（认证前 IP 槽位与认证后身份槽位）、App/Client/Subject 接入评估、客户端签名验证、Header 安全准备；**阶段二下游治理**（语义已在 DOMAIN.md 定义为权威设计，交付暂缓）——缓存、超时、熔断降级与 Header 转发应用。Client 直接承接原业务渠道身份，不增加渠道映射。

账户、登录会话生命周期、Token 签发/撤销、Role/Permission 归 user-auth；资源归属及最终业务授权归对应业务服务。gateway 不查询这些业务数据、不签发 Token、不把入口放行解释为最终授权。

下游服务不暴露公网；网络策略只允许 gateway 和授权内部工作负载。内部调用仍使用可信内网，不增加 workload identity/delegation。Header 自身不是跨不可信网络的身份凭据。

## 协作契约

- 外部 App/Client 标识使用 gateway v1 协议。App 声明只证明匹配到 ENABLED 配置；可信 ClientId 仅由有效凭据恢复。客户端版本是非可信声明。
- 有限的共享自定义 Header（CONTEXT/SIGNATURE）由 [framework-core RequestHeader](../../framework/docs/RESPONSIBILITIES.md) 枚举声明。gateway 管理名称扩展和禁止 set/remove 名单，TrustedHeaderResolver 仅按固定名称输出六种可信事实。HeaderPolicy 使用 trustedHeaders 集合，不支持重命名映射。API、存储和本地配置统一使用枚举选择。
- AccessGrantedContext 携带 VerifiedApp（appId、platform）、可选的 VerifiedClient（clientId、credentialId、verificationMode、verifiedAt）与可选的完整 AuthenticatedSubject；v1 决定保留 issuer 与 authenticationExpiresAt，不构造裁剪值对象。未验证的 Client 声明不进入上下文；入口获准不替代下游最终业务授权。快照版本记录在 AccessDecision 上。AuthenticatedSubject.authenticationExpiresAt 在 Host 路径来自已认证 Token exp，在 Browser 路径来自收到验证响应时刻加剩余 idle TTL，不是会话绝对期限。
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

v1 交付聚焦阶段一入口准入：三段式固定管道（准备/接入/准入）、IngressSnapshot 快照与发布、管理面四门面协议（definition/snapshot × command/query）、限流双槽位（EXACT）与 Header 安全准备。Header 转发清除属安全必需，随阶段一交付。阶段二下游治理（缓存、超时策略、熔断、降级）语义已在 DOMAIN.md 第 5 节保留为权威设计；其**执行实现已从代码树移除**，v1 请求链只做 headerApply。Bundle 内的 Timeout/CircuitBreaker/Cache 节点作为 ResolvedCapabilities 交接物保留并参与发布校验。

PREALLOCATED、动态插件、任意脚本、业务权限判定、全局瞬时撤销不属于本期能力。

管理端点最低访问要求在启动时对当前快照校验；运行期安全链继续独立执行同一领域最低授权规则。该启动校验不等同于后续每次发布的管理端点配置审查，也不替代运行期纵深保护。

## 验收边界

### Nacos 示例路径与摘要修复

修正两份打包示例中误写的 `/admin/gateway/capabilityComposition/...`，与实际 API 的 `/admin/gateway/policies/...` 路径一致；按现有规范化编码重新计算并同步示例摘要为 `f10716f0b79522fbb277e9961c8bb42d6cbcb958525c92f07176d6ee700aa47c`。未修改规范化算法或放宽摘要校验。固定向量测试增加管理 matcher 全集检查及路径篡改拒绝检查，防止单纯更新摘要掩盖路径漂移。外部开发环境若使用此前错误示例，需同步修正路径及摘要。

显式 Java 17 执行 `mvn -o -B -fae test -q`：323 项测试全部通过，0 失败、0 错误；`mvn -o -B -pl gateway-boot -am package -DskipTests -q` 与 `git diff --check` 通过。两个既有 Nacos 示例失败已解决，下方失败记录仅代表历史验证。未运行真实中间件冒烟。

### engineering-guidance 3.1.2 API 命名收口

按 refactor-service / develop-service 的 NAME-API-001、JAVA-RECORD-002、MAPSTRUCT-CONVERSION-001 完成三项确认范围：快照命令返回 SnapshotUpdateApiOutput，查询返回 SnapshotApiView；公共嵌套 API 载荷补齐 ApiCommand/ApiView；受影响命令与查询 record 防御性复制集合并保留 null。Java 类型名称变化要求调用方重新编译，HTTP 路径、JSON 字段和嵌套结构、凭据脱敏、存储摘要协议不变。application 的既有命令输出和读取端口不作机械更名，不新增空 Query 或转发服务。

验证（显式 Java 17）：`mvn -o -B -pl gateway-interfaces -am test -q` 通过 213 项测试；`mvn -o -B -pl gateway-boot -am package -DskipTests -q`、`mvn -o -B -pl gateway-integration-tests -am test-compile -DskipTests -q` 通过；`git diff --check` 通过。新增五项契约测试覆盖快照输出／视图序列化、嵌套命令反序列化、Header 未知字段拒绝、集合不可变及空值保留。复查受影响 Controller→Facade→application→端口链，业务决策及编排归属不变，domain/application 未新增 API、Spring 或外层依赖。本轮未执行全量测试或真实中间件冒烟；下述既有 Nacos 示例摘要欠账未纳入整改。


### Mapper 契约、结构映射与装配收口（2026-09-19）

按 refactor-service / develop-service 的 MAPSTRUCT-CONVERSION／SEMANTIC／CONTRACT／IMPLEMENTATION／REUSE／CONFIG 规则整改管理面与配置存储链：REST、定义和快照 Facade、Properties 均使用独立契约及抽象 MapStruct 实现，移除 Properties 内嵌字段复制和 Facade 散落的转换。存储投影与显式领域重建分离，canonical 排序、格式与摘要协议不变；凭据查询仍不回显 secretRef。子映射通过 uses 复用，生产 mapper 不再使用 Mappers.getMapper；两外层模块各有严格共享配置，修正管理自动配置的基础设施排序目标，并验证重叠扫描下每个契约只登记一个实例。

本轮不修改 Revision 的初次创建语义、CAS、签名、三段编排或下游迁移；测试仅适配新构造器并补充映射边界。文档仍只有 README.md、docs/DOMAIN.md、docs/RESPONSIBILITIES.md 三份。

验证（显式 Java 17）：`mvn -o -B -pl gateway-infrastructure,gateway-interfaces -am test -q` 通过 243 项测试；新增覆盖目标 AppId、集合隔离及不可变性、空权限集合领域规范化、凭据有效期及脱敏、快照版本、Spring 唯一装配和存储往返摘要一致性。`mvn -o -B -pl gateway-boot -am package -DskipTests -q`、`mvn -o -B -pl gateway-integration-tests -am test-compile -DskipTests -q` 与 `git diff --check` 通过；未运行真实中间件冒烟。

全量 `mvn -o -B test -q` 仍失败于既有 StoredPolicyDefinitionCodecTest / StoredAccessSnapshotCodecTest 示例摘要校验，实际摘要仍为 `b7c4297d6faefd4101d722c8ac0aabb84ade787100f608b0199ac619745c2468`，本轮未重算示例摘要；全量测试不视为通过。

### 通用能力槽位与执行记录（2026-09-19）

当前能力执行边界以本节及 DOMAIN.md 为准，下方记录保留为历史过程。

- domain：CapabilityResolver 统一解析两个槽位，移除限流专用 preAuthRules；CapabilityDefinition 声明槽位、所需事实与重复策略，CapabilityPlanner 生成强类型执行计划并负责跨槽位去重。RateLimitCapability 持有限流维度与单元生成规则。
- application：准备／准入段只构造槽位上下文并交接产出；CapabilityExecutionCommandService 统一编排解析、规划、批次执行，拒绝短路并传播取消。入口使用 CapabilityExecutionCommand，返回 CapabilityExecutionOutput；解析节点与执行记录来自同一次调用，准入段后续消费 Header 不重新解析。限流批次仍执行全部规则后聚合，不因某条超限而跳过余下规则。
- interfaces：CapabilityResponseWriter 分发强类型结果投影；两个过滤器不再持有限流执行记录或生成配额维度。缺失拒绝结果投影时关闭请求，不静默放行。
- 交接：Prepared／AdmissionStageOutput 使用 CapabilityExecution；AdmissionStageCommand 只持有 Allowed，从同一 Prepared 获取累计执行键，删除重复 applied 和旧 QuotaExecution。
- 本期只登记限流；未知／歧义路径的全局 IP 保护、单请求单快照、签名、Redis 键及快照格式不变。身份绑定 IP 规则在 POST_AUTH 首次执行；不启用阶段二能力，不增加文档或模块依赖。

编排收敛复验（显式 Java 17）：`mvn -o -B -pl gateway-interfaces -am clean test -q` 通过 199 项测试，`mvn -o -B -pl gateway-boot -am package -DskipTests -q` 与 `git diff --check` 通过。新增验证覆盖解析结果原样交接、双槽位用例去重、规划异常不触发执行、命令集合不可变及准备段取消传播。按 refactor-service / develop-service 复查 `ARCH-ORCHESTRATION-COHESION-001`、`APPLICATION-COMMAND-001`：解析→规划→执行统一归现有能力服务，阶段只构造上下文并交接 Output；Resolver/Planner 仍是领域规则，不新增转发层或模块依赖。全量 `mvn -o -B test -q` 仍失败于 StoredPolicyDefinitionCodecTest / StoredAccessSnapshotCodecTest 的既有示例摘要不一致，相关在途文件未修改；未运行真实中间件冒烟。

验证（显式 Java 17）：`mvn -o -B -pl gateway-interfaces -am clean test -q` 通过 194 项测试；`mvn -o -B -pl gateway-boot -am package -DskipTests -q` 通过；`git diff --check` 通过。新增覆盖第二种测试能力的双槽位／重复策略、事实依赖、身份绑定 IP 延后执行、批次顺序与拒绝短路、执行器登记检查、取消传播及 HTTP 拒绝投影。全量 `mvn -o -B test -q` 仍在下述两项 Nacos 示例摘要测试失败，未修改相关示例或规范化格式；未执行真实中间件冒烟。

### 入口阶段与组合编排分离（2026-09-19）

`AccessSnapshotWebFilter` 更名为 `IngressPreparationWebFilter`，`TrustedRequestContextGlobalFilter` 更名为 `IngressAdmissionGlobalFilter`。前者注入准备段服务；后者注入独立的 `IngressAdmissionCommandService`，不再逐请求创建应用服务。

组合服务持有接入段和准入段，负责拒绝短路及跨阶段取消／异常；AdmissionStageCommandService 不再持有接入段，仅接收 Allowed。组合结果 IngressAdmissionOutput 区分 AccessDenied 与 Evaluated；AdmissionStageOutput 三项均非空，配额拒绝仍是有效评估结果。单快照、认证选择、签名及配额规则不变。

本地入口继续复用准入过滤器；修正 LocalPolicyWebFilter 将 String 与 RouteId 直接比较的问题，改为比较 routeId.value()，补充本地端点确实进入准入、代理端点不提前进入准入的测试。DOMAIN.md 第 4.4 节明确区分管理最低授权检查与通用入口准入，恢复本地复用分支不取消 Spring Security 的独立最低授权要求。

按 refactor-service / develop-service 核对 `ARCH-ORCHESTRATION-COHESION-001`、`INTERFACES-RESPONSIBILITY-001`：顺序与短路属于 application，HTTP 证据、路由核对、响应及 Header 应用保留 interfaces；未增加模块依赖。

本轮验证（显式 Java 17）：`mvn -o -B -pl gateway-interfaces -am clean test -q` 成功，183 项测试全部通过（domain 107、application 64、interfaces 12）；`mvn -o -B -pl gateway-boot -am package -DskipTests -q` 成功；`git diff --check` 通过。新增覆盖准备防重入、组合认证／配额取消与异常、拒绝短路、配额拒绝与接入拒绝区分、本地路由识别。

全量 `mvn -o -B test -q` 未通过：未改动的 Nacos 示例摘要断言／加载测试失败，当前规范化摘要为 `b7c4297d6faefd4101d722c8ac0aabb84ade787100f608b0199ac619745c2468`，示例仍为 `5034572782b078b77e6bab3c6ad45bb7b709b4dc64c42394d22e2842e6e8fb87`。本轮未修改规范化格式或重写示例来覆盖其他在途变更；未运行真实中间件冒烟。

### 配置管理边界与准入阶段职责修正（2026-09-19）

当前实现以本节和 DOMAIN.md 为准，下方同日记录保留为历史过程。

- application.access 迁至 application.configuration；领域访问规则迁至 domain.ingress.access，完整入口快照、内容及版本迁至 domain.ingress.snapshot。快照与内容名称为 IngressSnapshot / IngressContent。
- 发布与回退共用 IngressSnapshotUpdateOutput；锁顺序、CAS、回退阻断与幂等重入不变，外部配置键和 dataId 不变。
- AccessStageCommandService 只认证并形成访问决策；AdmissionStageCommandService 使用 Allowed 携带的同一 Prepared 解析能力、生成配额维度并执行 post-auth 配额。Header 消费 AdmissionStageOutput.capabilities，不在接入段预先解析。
- 快照低版本／同版本幂等／同版本冲突／高版本接纳规则由 IngressSnapshot 决定，Nacos 只执行技术切换。
- Subject 的 OPTIONAL 更名为 ANONYMOUS，现有结果接纳规则不变；Client 的 OPTIONAL 不变。示例重新计算摘要为 `5034572782b078b77e6bab3c6ad45bb7b709b4dc64c42394d22e2842e6e8fb87`，外部开发配置须更新枚举值并重新生成摘要。

安全边界：当前认证适配器未提供“签名及其余认证检查通过、仅过期”的独立 EXPIRED 分类，因此不能将领域对 EXPIRED 的支持描述为请求链已支持过期匿名放行。本轮不改变双凭据选择、失败回退或“已声明 Client 但未提交签名”的既有行为；后者在 OPTIONAL 下不生成可信 Client。

验证（显式 Java 17）：`mvn -o -B clean test -q` 通过 286 项单元／模块契约测试，失败、错误、跳过均为 0；`mvn -o -B -pl gateway-boot -am package -DskipTests -q` 成功；`git diff --check` 通过。集成测试仅编译，未运行真实基础设施冒烟，不改变下游 Header 迁移暂缓状态。

按 refactor-service / develop-service 复核受影响链路：`ARCH-ORCHESTRATION-COHESION-001` 的能力解析编排位于 application；`ARCH-DOMAIN-COHESION-001` 的快照接纳判定位于 domain；`NAME-PACKAGE-001` 的配置／访问／快照边界已区分；domain/application 无外层实现、Spring 或 Reactor import。另修复已有 Capability 仓储调用不存在配置 getter 的编译阻断，使用现有 getter，不改变部署键或 dataId。

### 阶段命名收口与访问段输入收敛（2026-09-19）

本轮把三阶段管道的命名与输入边界收口到一致状态。

**命名**：`IngressAccess*` 简化为 `Access*`（20 个标识）；随后统一用 `Stage` 标记管道阶段——三个服务为 `PreparationStageCommandService` / `AccessStageCommandService` / `AdmissionStageCommandService`，命令与输出同构（`PreparationStageCommand`、`AccessStageCommand`、`PreparationStageOutput`、`AccessStageOutput`、`AdmissionStageOutput`）。`IngressTask`、`IngressStagesTest` 保留 `Ingress` 前缀：裸名 `Task`/`Stages` 过于宽泛，前缀在此是有用限定词。

**输入边界**：确立「command 装事实、方法参数装协作者」的判据。据此 `AccessStageCommand` 由三字段扩为五字段，纳入 `signatureEvidence`（匹配后采集的事实，null 表示未提交签名）与 `applied`（准备段的配额执行记录）；`AccessStageCommandService.evaluate(command, subjectAuth)` 成为接入段唯一入口，内部完成客户端验证、主体认证 join 与接入决策，`authenticateClient` 收为私有；`AdmissionStageCommandService.admit(command, subjectAuth)` 由四参数降为两参数，只负责调用接入段再执行认证后配额。`subjectAuth` 保持 `Supplier` 参数而非进 command：它不是事实，且必须延迟调用以便歧义与未知路径直接拒绝、不触发认证。取消传播经 `IngressTask` 递归级联，行为不变。

**阶段交接**：确立统一规则——**每条边只携带上一段的成功产出，且命令直接包裹它而非重述其字段**。据此 `AccessStageCommand(PreparationStageOutput.Prepared prepared, ClientSignatureEvidence signatureEvidence)`、`AdmissionStageCommand(AccessStageOutput.Allowed access, Set<RateLimitRuleId> applied)`；拒绝不进管道（准备段的拒绝在边缘终止，接入段的拒绝由组合入口直接送终态）。包裹使交接带类型：接入段命令不可能由准备段产出以外的东西构造。`PREPARED` exchange 属性承载 `PreparationStageOutput.Prepared` 本身，取代原先拆散的 `SNAPSHOT`/`ENDPOINT_MATCH`/`EVIDENCE`/`APPLIED_IP_RULES` 四个键。`SNAPSHOT` 一并删除（其唯一消费者只拿它判空）；`ENDPOINT` 保留为唯一匹配端点的非规范化视图，供安全链转换器与本地路由过滤器使用。准入段入口两条：`admit(command)` 为段本体（可独立调用与测试），`admit(accessCommand, subjectAuth)` 为请求路径的组合入口。唯一不沿链走的边是 `applied`（准备段 → 准入段，绕过接入段）——这是刻意的，只有准入段读它。

**产出抽象**：`AccessStageOutput` 的两个变体改为**持有** `AccessDecision` 本身（`Denied(AccessDecision.Denied)`、`Allowed(AccessDecision.Allowed, policy, dimensions)`），取代原先「拒绝包裹决策、允许解包决策」的不对称写法。此前 `Allowed` 丢掉决策、因而丢掉快照版本，违反 2.4「两种结局都携带请求所持快照版本」的约定；现在两个变体都带决策，本类型与 `AccessDecision` 的关系是包含而非平行层级。同时删去 `Allowed.headers`——它恒等于 `policy.headers()`（`CapabilityResolver.resolve` 保证非 null），单列只会制造一份可能与策略不一致的第二值；消费点改为 `allowed.policy().headers()`。顺带修正该类型被打乱的 Javadoc。

**破坏性变更**：五组类型更名；`AccessStageCommand` 与两个服务方法签名变化；`GatewayRequestAttributes` 删去 `SNAPSHOT`/`ENDPOINT_MATCH`/`EVIDENCE`/`APPLIED_IP_RULES`、新增 `PREPARED`；`AccessStageOutput.Allowed` 的组件由 `context` 改为 `decision`（`allowed.decision().grantedContext()`）。

验证（显式 Java 17，离线构建）：`mvn -o -B clean test` → BUILD SUCCESS，12 模块全绿，284 项测试 0 失败。

**待决**：`CapabilityResolver` 的调用点在 `AccessStageCommandService.decide`，而 DOMAIN.md 第 1.1/1.3/4.3 节把「授权及绑定解析」放在准入段——文档与代码对**能力放置**的判断不一致，本轮未改动任何一方。

### 标识值对象迁移与遗留缺陷修复（2026-09-16）

本轮完成 `Revision` 与 `SnapshotVersion` 值对象在 domain/application/infrastructure 的贯通，并修复此前遗留的五项缺陷。

标识：`Revision(String)` 表示源修订令牌，`SnapshotVersion(long)` 表示正数快照版本（取代原 `AccessSnapshotVersion`）。`RevisionedAccessDefinition`、`RevisionedAccessSnapshot`、`IngressSnapshot.version()`、`WritableAccessDefinitionRepository.compareAndSet`、`AccessSnapshotPublisher.compareAndSet`、`AccessSnapshotHistory.find`、`RollbackAccessSnapshotCommand` 与 editing 层全部命令服务均使用这两个值对象。**边界**：`gateway-api` 的 DTO 保持 `String sourceRevision` 与 `long snapshotVersion` 作为线上协议，转换只在 `gateway-interfaces` 门面内进行。`AccessSnapshotAssembler.assemble` 收 `SnapshotVersion`，版本递增由 `assembleUpdate` 内部完成，调用方不再计算版本；至此应用层与基础设施层不存在裸 `long`/`String` 标识。

缺陷修复：LKG 文件存储改为循环写 + 写后 force + 移动后 fsync 目录，原子移动不受支持时降级为「先复制再 force 目标」；限流适配器新增 `gateway.ratelimit.redis.acquire-timeout`（默认 500ms），以有界线程池 + 超时界定阻塞调用，超时与池满降级为 `unavailable` 交给 RedisFailurePolicy，同时保证非依赖类异常原样抛出；`NacosAccessSnapshotStore.compareAndSet` 在远端 CAS 成功后不再因本地投影失败而向外报错，避免调用方重发；`NacosAccessSnapshotHistory.find` 由引用比较改为值比较。

错误归属：`PolicyYamlCodec` 只报自身层级的 `INFRA_TECH_POLICY_DESERIALIZATION_FAILED`，不区分失败原因；三个 decode 调用点（`NacosAccessSnapshotStore.accept`、`AbstractNacosAccessDefinitionRepository.load`、`NacosAccessSnapshotHistory.find`）各自按语境转换为 `INFRA_ACL_NACOS_CONTENT_INVALID`，领域校验原因作为 cause 保留在堆栈中。基础设施层不重新分类领域异常。

端口命名对齐：`AccessSnapshotProvider.current()` 更名为 `AccessSnapshotCapturer.capture()`，与 DOMAIN.md 第 1.3 节的能力名 `snapshotCapture` 及第 4.1 节准备段第一步同名；捕获现有版本、不创建新版本（创建归 `AccessSnapshotPublisher`），每请求恰好捕获一次。实现类与测试类同步更名为 `PropertiesAccessSnapshotCapturer` / `PropertiesAccessSnapshotCapturerTest`；`NacosAccessSnapshotStore` 同时实现捕获与发布两个端口，名称不变。

**破坏性变更**：规范化内容摘要再次变化，canonical contentHash 为 `fd603a9c3e085bc97c193b2ddf641320c39951cda77f5b64684046399e212a4a`；本地只读配置键由 `gateway.policy-definition` 更名为 `gateway.access-definition`。外部开发快照与本地配置必须同步更新。

验证（显式 Java 17，离线构建）：`mvn -o -B clean test` → BUILD SUCCESS，12 模块全绿，284 项测试 0 失败（domain 106、application 60、infrastructure 35、nacos 21、coordination-redis 3、security-spring 29、replay-redis 4、ratelimit-redis 18、interfaces 8）。集成测试模块编译通过但未运行。

**已知欠账**：gateway-integration-tests 仅完成编译适配，七个场景本轮均未在真实基础设施上运行（Docker 未使用）。

### 阶段二摘除与在途重构收尾（2026-09-15）

本轮把阶段二下游治理从 v1 摘除，并修完上一次重命名重构留下的全部编译阻断。

阶段二：`TrustedRequestContextGlobalFilter` 不再调用下游治理；缓存、熔断、超时、降级的执行实现（application/governance、infrastructure/governance、interfaces 的 DownstreamPolicyHandler/DownstreamResponseLifecycle/GatewayResponseWriter、domain 的 CacheRequestFacts/CacheResponseFacts/DownstreamGovernanceRules 及其测试，共 24 个文件）已移除，移除前完整备份在 `.workbuddy-ai/backup/stage-two-20260915/`。Bundle 内的 Timeout/CircuitBreaker/Cache 节点保留为 ResolvedCapabilities 交接物。

请求链语义：准备段写入完成标记与已判定匹配结果，接入段消费该结果而不再重新推导；`CapabilityComposition.validate` 接入 `AccessSnapshotAssembler`，发布前校验绑定引用与同层竞争；管理查询端点登记 GET matcher 并由 `ManagementAuthorizationManager` 校验 Host 会话声明；歧义匹配补上 `gateway.ingress.ambiguous_endpoint_match` 低基数指标。

模型对齐：端点接入状态并入端点 Definition（`access` 节点），凭据转发改由 HeaderPolicy 承载；infrastructure 层从 `Map<Id, XAccess>` 端口到 registry 模型；`RateLimitKeyResolver`、`BrowserSessionVerifierAdapter`、`Credential`/`ClientAccess`/`Endpoint` 构造点全部对齐当前领域类型。

验证（显式 Java 17，离线构建）：`mvn -o -B clean test` 下 gateway-domain 106、gateway-application 60、gateway-infrastructure 35、gateway-infrastructure-persistence-nacos 21、gateway-infrastructure-coordination-redis 3、gateway-infrastructure-security-spring 29、gateway-infrastructure-replay-redis 4、gateway-infrastructure-ratelimit-redis 17、gateway-interfaces 8 项测试通过，失败与错误均为 0。示例资源按新 schema 重新生成，canonical contentHash 变更为 `43c00446c56e49ab1a72f5f391c6c528a772b047ad51039b15de406d02b9735f`，外部开发快照必须重新生成。

**已知欠账**：gateway-integration-tests 的七个场景尚未对齐新管理面协议与新端点模型，该模块测试编译未通过，`mvn verify` 仍不可用；本轮未运行任何真实基础设施冒烟（Docker 未使用），因此上述数字只代表单元／模块契约层。另需注意：本地 `~/.m2` 中存在 2026-09-08 安装的 `0.3.0-SNAPSHOT` 陈旧构件，`mvn -pl <模块>` 不带 `-am` 会对其编译，所有局部验证必须带 `-am` 或从根工程执行。

### 两阶段模型与管理面协议文档同步（2026-09-13）

本轮全面重写 DOMAIN.md（两阶段生命周期、三段式入口准入、能力库与固定拓扑、阶段二语义保留为权威设计）与本文件职责/交付表述；管理面接口定稿为四个门面（IngressAccessDefinition/Snapshot × Command/Query，content-only 保存与独立状态转换，文件级 sourceRevision CAS 语义）。

验证：`mvn test -pl gateway-domain` 通过 107 项测试（0 失败）；`mvn compile -pl gateway-api` 通过；`git diff --check` 通过。domain 内 policy/security 包的历史编译错误已修复（缺失 import、判重条件反转、HeaderRules 规范化存储、HeaderPolicy 空值校验顺序）。当时的欠账是 interfaces/application 尚未对齐新管理面协议、reactor 整体编译未通过；**该欠账已在 2026-09-15 轮次结清**（实测 interfaces 与 application 均能编译并通过各自测试，真正的断裂在 infrastructure 与 nacos），本节描述的是当时状态，不代表当前工作区。

### 接入命名与规则文档同步（2026-09-10）

本轮仅同步已确认设计：AccessGrantedContext 命名及可空子上下文、按主体类型校验认证信息、ClientVerificationMode 的 null/REQUEST_SIGNATURE 语义，以及 Host 端点可不配置 requiredAudiences。领域规则以 [DOMAIN.md](DOMAIN.md) 为准；不新增文档，不修改 Java、配置、测试或其他在途内容。

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
