# gateway 职责与交付 · v1

领域模型及 v1 技术方案见 [DOMAIN.md](DOMAIN.md)，运行说明见 [README.md](../README.md)。

## 职责边界

gateway 将外部不可信请求转换为受控入口请求。请求生命周期分为两个宏观阶段：**阶段一入口准入**——快照捕获、端点匹配、限流、App/Client/Subject 接入评估、客户端签名验证、Header 安全准备；**阶段二下游治理**——已交付超时与 Header 转发应用。Client 直接承接原业务渠道身份，不增加渠道映射。

账户、登录会话生命周期、Token 签发/撤销、Role/Permission 归 user-auth；资源归属及最终业务授权归对应业务服务。gateway 不查询这些业务数据、不签发 Token、不把入口放行解释为最终授权。

下游服务不暴露公网；网络策略只允许 gateway 和授权内部工作负载。内部调用仍使用可信内网，不增加 workload identity/delegation。Header 自身不是跨不可信网络的身份凭据。

## 协作契约

- 外部 App/Client 标识使用 gateway v1 协议。App 声明只证明匹配到 ENABLED 配置；可信 ClientId 仅由有效凭据恢复。客户端版本是非可信声明。
- 有限的共享自定义 Header（CONTEXT/SIGNATURE）由 [framework-core RequestHeader](../../framework/docs/RESPONSIBILITIES.md) 枚举声明。gateway 管理名称扩展和禁止 set/remove 名单，TrustedHeaderResolver 仅按固定名称输出六种可信事实。HeaderPolicy 使用 trustedHeaders 集合，不支持重命名映射。API、存储和本地配置统一使用枚举选择。
- AccessGrantedContext 携带 VerifiedApp（appId、platform）、可选的 VerifiedClient（clientId、credentialId、verificationMode、verifiedAt）与可选的完整 AuthenticatedSubject；v1 决定保留 issuer 与 authenticationExpiresAt，不构造裁剪值对象。未验证的 Client 声明不进入上下文；入口获准不替代下游最终业务授权。快照版本记录在 AccessDecision 上。AuthenticatedSubject.authenticationExpiresAt 在 Host 路径来自已认证 Token exp，在 Browser 路径来自收到验证响应时刻加剩余 idle TTL，不是会话绝对期限。
- gateway.headers 在启动时生成不可变 HeaderAdmissionRules；保存、发布、快照编码/加载均校验名称准入及操作限制。HeaderPolicy 随快照动态更新，但部署规则当前变更需重启；不提供独立规则热刷新。此限制避免运行请求混用未经联合校验的规则和策略。
- 未发布阶段直接破坏式更新，不注册历史 Header 名称、LEGACY 分类或兼容映射，也不专门清除历史名称。未验签 Client 声明不能转为可信 CLIENT_ID。user-auth/framework-starter 读取逻辑迁移仍暂缓，现有下游必须迁移后才能按新契约协作；本次不改动这两个工程。历史签名算法按既定需求保留，与 Header 历史名称无关。
- Host Session 使用 JWT/JWK 或 Reference Token introspection，统一恢复 subjectId/sessionId；验证 issuer、时间、audience、scope 和必需声明。两种技术路径不泄露到领域上下文。
- 端点的 requiredAudiences/requiredScopes 是可选的附加访问要求（子集 AND 校验），不替代认证阶段校验；管理入口最低要求为 REQUIRED 主体、admin scope 和 gateway audience。
- Browser Session 使用 user-auth-api 发布的 Facade 路径与载荷，经受管理 serviceId 的内部实例调用。sessionId 保持现有返回身份；user-auth 管理 idle TTL、绝对期限和父会话。gateway 不刷新 Cookie。
- Browser Session 无效返回 401，不可达 503，非法响应 502；不可降级为匿名、宿主或缓存身份。非安全请求严格验证 App.allowedOrigins。
- /api/user-auth/** 与 /admin/user-auth/** 路由剥离服务段；/oauth2/** 与 /.well-known/** 原样转发。/internal/** 不登记也不公开路由。
- 管理端点要求 HOST_SESSION、admin scope 和 gateway audience。OAuth2 协议端点可按显式策略保留 Authorization；Cookie 只按名称白名单转发。
- 历史签名算法继续可用；新接入采用 HTTP_REQUEST_SIGNATURE_V1。协议迁移不等于密钥轮换，不允许验签失败降级。

## 交付状态

v1 交付聚焦阶段一入口准入：三段式固定管道（准备/接入/准入）、IngressSnapshot 快照与发布、管理面四门面协议（definition/snapshot × command/query）、限流双槽位（EXACT）与 Header 安全准备。Header 转发清除属安全必需，随阶段一交付。

阶段一交付入口准入与 EXACT 限流；阶段二交付 `timeout` 与 `headerApply`，在 FORWARD 槽位应用超时预算和 Header 转发计划。CapabilityBundle 已改为开放能力配置集合，当前内建限流、超时与 Header 三项能力。

执行模型只包含 PRE_AUTH、POST_AUTH、FORWARD 三个固定执行点，以及四个端点/获准身份可用性事实。连接/响应超时、总预算、502/504 映射和提交后异常传播由协议适配层处理。CapabilityOutcome 只保留 CONTINUE/STOP 流转处置，不再提供 kind 分类；限流判定与 Header/超时计划仍由各自强类型载荷表达。

PREALLOCATED、动态插件、任意脚本、业务权限判定、全局瞬时撤销不属于本期能力。

能力配置与运行模型分别位于 domain.capability.configuration / runtime。有效配置统一为 EffectiveCapabilityConfig，application 的固定 CapabilityRegistration 每次绑定当前配置，生成直接携带 N 的请求级 CapabilityPlanningEntry；无状态 Planner 不引用 EffectiveCapabilityConfig。有效配置通过代码注册的类型化键读取，配置定义持有合并、默认值、同层竞争及跨 Bundle 校验规则。应用服务构造时双向检查运行能力与执行器，生产装配校验配置／运行目录一致；编解码器校验配置／协议目录一致。Bundle 的 API／配置载荷改为 capabilities 容器，旧 schema 不兼容，示例快照摘要已同步更新；执行点、签名、配额键和 Header 安全语义不变。

管理端点最低访问要求在启动时对当前快照校验；运行期安全链继续独立执行同一领域最低授权规则。该启动校验不等同于后续每次发布的管理端点配置审查，也不替代运行期纵深保护。

能力组合保存已接入 AccessDefinitionCommandFacade 与 `PUT /admin/gateway/policies/configuration`：整份替换 bundles／bindings，校验注册能力与 Header 准入，复用 revision／回退阻断／CAS 后返回新 sourceRevision。保存不自动发布、不改变运行快照；只读部署不装配该写 Controller。HTTP 与 Nacos 复用注册编解码规则，不另建 HTTP 能力目录。

本轮 HTTP 契约测试发现并修正 Definition 写 Controller 缺少 WebFlux Controller 标记导致的 404，并将它排除在启动组件扫描之外，保留条件装配。其余三个管理 Controller 仍只有 RequestMapping／ResponseBody，存在同类 HTTP 注册缺口，未在本轮扩展修复；发布、回退与查询用例／Facade 已有实现，不代表这些 HTTP 入口已完成可达性验收。

## 验收边界

当前模型以以上交付状态及 DOMAIN §5.3 为准；下方按日期保留的演进记录只描述当时实现，不定义当前执行点、事实或框架选型。

### 能力组合管理保存接入（2026-09-26）

已打通 GatewayAccessDefinitionCommandController → AccessDefinitionCommandFacade.saveCapabilityComposition → CapabilityCompositionApiMapper → CapabilityCompositionCommandService.save。复用现有整体替换、发布锁、回退阻断与 Definition CAS，不新增发布副作用。能力节点复用 CapabilityConfigCodecs，interfaces 单向依赖基础 infrastructure 的共享编解码组件，不依赖 Nacos 等具体存储模块；domain/application 未增加外层依赖。未修改线上错误码或快照摘要格式。

依照 develop-service / refactor-service 的 INTERFACES-RESPONSIBILITY-001、MAPSTRUCT-CONVERSION-001、ARCH-DOMAIN-COHESION-001 与 PACKAGE-DIRECTION-001 核对：HTTP 绑定与错误投影留在 interfaces；结构映射使用独立契约和严格 MapStruct 配置；能力规则留在 domain，保存协调复用 application。为使写入口真正可达，Definition 写 Controller 增加 RestController 标记并从 boot 组件扫描排除；其请求统一使用 ClientRequestBody，确保可信上下文覆盖发生在校验前。

Java 17 下 `mvn -o -B clean test -q`：391 项测试通过，失败、错误、跳过均为 0；`mvn -o -B -pl gateway-boot -am package -DskipTests -q` 及 `git diff --check` 通过。新增 9 项单元／模块契约测试覆盖 HTTP 到保存用例的真实转换链、返回 revision、全量替换、非法能力／字段／标识／Header 拒绝、旧 revision 冲突、回退阻断、Facade 代理校验、可信上下文绑定和可写用例条件装配。HTTP 测试使用内存 WebFlux，不代表完整安全链或真实中间件验收；集成测试仅完成构造器编译适配，未运行冒烟，未提交。

范围外问题：另外三个管理 Controller 的 HTTP 注册缺口保留，详见当前交付状态；本轮没有将其注解形状测试当作 HTTP 可达性证据。

### 开放能力配置集合（2026-09-26）

CapabilityBundle 改为按 CapabilityId 索引的强类型 CapabilityConfig 集合；CapabilityConfigDefinition 持有默认值、合并、同层竞争及跨 Bundle 校验，CapabilityConfigRegistry 负责注册和通用分派。EffectiveCapabilityConfig 通过代码拥有的 EffectiveCapabilityKey 读取；唯一受控的泛型转换位于已逐项校验的列表键中。新增能力不修改 Bundle／Resolver／Planner，只新增能力实现及配置、运行和协议注册。

依照 develop-service / refactor-service 复核 ARCH-DOMAIN-COHESION-001、ARCH-ORCHESTRATION-COHESION-001、PACKAGE-DIRECTION-001、MAPSTRUCT-CONVERSION-001：配置规则留在 domain；application 继续编排解析、规划和执行；协议 DTO 与注册编解码留在 infrastructure，结构投影使用共享配置下的 MapStruct。通用 configuration 包不引用具体能力或默认目录，runtime 不依赖有效配置容器；本轮未新增模块依赖。

Java 17 下 `mvn -o -B clean test -q`：382 项测试通过，失败、错误、跳过均为 0；`mvn -o -B -pl gateway-boot -am package -DskipTests -q` 及 `git diff --check` 通过。新增自定义能力测试覆盖解析、规划执行、存储往返、非法注册／未知配置拒绝及载荷防御性复制。本地配置和 Nacos 示例已迁移 capabilities 结构并更新快照摘要。集成测试仅编译，未运行真实中间件冒烟；未提交。

当时记录的能力组合保存 Facade／Controller 缺口，已由后续本轮管理入口接入工作解决；其 HTTP 交付边界以上方当前状态为准。

### 请求级规划输入与编译断点修复（2026-09-26）

CapabilityPlanningEntry 直接持有能力定义与 N configuration；配置节点遵循不可变契约，允许 null 的能力（当前为 timeout）由自身决定不生成单元。CapabilityPlanner 无状态，按调用传入的 Entry 列表规划，不再依赖 EffectiveCapabilityConfig。固定注册项 CapabilityRegistration 留在 application，绑定当前有效配置后生成请求级 Entry；服务构造时统一校验能力／执行器完整性及重复注册。准备、准入、转发均复用该应用编排，未增加 HTTP 层业务编排或共享请求状态。

一并修复获准处理的在途断点：缺失 Require 引用、残留 DomainRequire、领域异常工厂及错误枚举引用不一致、Header 名称规范化判重返回类型错误。按当前 develop-service 的 JAVA-DOMAIN-GUARD／FACTORY 约束保留领域异常工厂，公共领域错误的原编号 001/002/003 不改义；按键注册表使用 JDK 不可变 Map 收集及明确的重复键拒绝，不依赖有问题的按键判重重载。集合先领域校验再防御性复制，避免非法元素提前变成 JDK 异常。保留已有 name()、record 载体与 Header 前缀等其他在途改动。

按 PACKAGE-DIRECTION-001、ARCH-DOMAIN-COHESION-001、ARCH-ORCHESTRATION-COHESION-001 复核：运行模型与配置组合模型互不引用；领域持有筛选、去重及不变量，应用持有配置绑定和批次执行。真实装配测试覆盖三个能力；新增重叠请求以相反顺序完成的配置隔离验证、随后无超时配置不继承旧预算，以及启动期重复注册／来源缺失拒绝验证。

显式 Java 17 串行执行 `mvn -o -B clean test -q`：**369 项测试全部通过，失败、错误、跳过均为 0**；`mvn -o -B -pl gateway-boot -am package -DskipTests -q` 和 `git diff --check` 通过。集成测试只编译，未执行真实中间件冒烟；未提交。

范围外观察：App、Client、Endpoint 当前在途 record 仍使用全字段相等性，尚未落实此前讨论的聚合标识相等性；可能影响同标识不同属性对象的 Set 去重。本轮仅修复其编译／校验，不改动用户选定的聚合载体形态，注册表仍按 ID 显式判重。

### 能力配置与运行模型边界重构（2026-09-23）

composition / execution 分别迁为 configuration / runtime；ResolvedCapabilities 替换为 EffectiveCapabilityConfig，删除 ResolvedCapabilityNodes。CapabilityPlanningEntry 用泛型配对能力及配置来源，CapabilityRegistration 双向检查执行器覆盖；真实装配契约测试验证限流、超时及 Header 三项配置实际进入计划／执行。Resolver 只消费端点与获准身份，FORWARD 复用准入段有效配置。Composition 校验直接接收三个注册表，不反向依赖完整快照；Header 缺失明确拒绝，未配置超时仍不生成单元。

按 refactor-service / develop-service 复核 PACKAGE-DIRECTION-001、ARCH-DOMAIN-COHESION-001、ARCH-ORCHESTRATION-COHESION-001：绑定解析、事实筛选和去重留在 domain；application 编排解析／规划／执行及短路；interfaces 仅装配入口并应用 HTTP 计划。新增依赖守卫覆盖配置及纯规则包不得反向依赖 runtime，配置包不得依赖 snapshot；未增加模块依赖或转发服务。Java 调用方需适配新名称和签名，线上字段、管理路径及规范化摘要不变。

最终显式 Java 17 串行验证：`mvn -o -B clean test -q` 通过 **366 项测试，0 失败、0 错误、0 跳过**；`mvn -o -B -pl gateway-boot -am package -DskipTests -q` 通过；`git diff --check` 通过。集成测试模块只编译，未运行真实中间件冒烟；下游 Header 迁移仍暂缓。本轮未提交。

### 执行点与输入事实收敛（2026-09-22）

输入事实收敛为端点与获准身份的可用性，移除恒真或重复的 REQUEST/FORWARD_READY。Context 保留阶段必填约束，事实仅由端点与获准身份推导。现有超时与错误投影继续由接口层处理，CapabilityOutcome 保持不变。按 develop-service 的职责边界检查，domain 保留输入不变量与规划规则，application 保留能力执行编排，interfaces 保留协议错误投影；没有新增模块依赖。

显式 Java 17 执行 `mvn -o -B -pl gateway-interfaces -am clean test` 与 `git diff --check` 均通过。新增测试覆盖匿名获准与可信身份区别、阶段必填项、连接失败 502、传输超时 504、未分类异常与提交后异常传播。未运行全量模块测试或真实中间件冒烟。

### headerApply 能力化与 spring-cloud 组件边界（2026-09-22 续四）

**headerApply 能力化**：`HeaderApplyCapability`（`SingleValuedCapability<HeaderPolicy>`，`FORWARD`，`ONCE_PER_REQUEST`）+ `HeaderApplyCapabilityExecutor`（只把计划带出来）+ `ResolvedCapabilities.nodes()` 映射。**形状与 `timeout` 一致**：单值能力只「声明」，落地动作在拥有请求对象的适配器——领域决定「计划是什么、必须被应用」，适配器决定「怎么改这次请求」。这也是它能留在领域层而不碰 Spring WebFlux 的原因。

**两处刻意的行为变化**：Header 应用从「准入放行后立刻」移到「`FORWARD` 相之后、发起调用之前」，因此 (a) **被拒绝的请求不再被改写**，(b) `FORWARD` 相消费的请求证据仍是**清除前**的。**失败关闭**：`FORWARD` 相未产出计划时返回 500 而非转发；该分支目前不可达（resolver 恒给出默认计划），存在意义是防止将来某次改动把它变成静默泄露。

**验证非空转**：新增适配器用例断言「调用方伪造的身份 Header 在到达下游前被替换为验证值」；反向验证时去掉计划应用，该用例失败（`expected: <[app]> but was: <[forged]>`），确认它真的覆盖了新路径而非恒真。

**spring-cloud 组件边界（设计结论，已写入 DOMAIN §5.3）**：

- SCG 的 **route metadata 超时已接入**：`RouteTimeoutMetadata`（interfaces）在 `FORWARD` 相发起调用前把 `connect`/`read` 写进命中路由的 metadata——`connect-timeout`（毫秒，Netty `CONNECT_TIMEOUT_MILLIS`）与 `response-timeout`（毫秒）；重建路由时保留原有 metadata 与身份，`total` 仍由 Reactor `timeout` 施加。**只实施 `total` 是不够的**：连接建立阶段没有响应头也没有响应体，只有客户端自己的连接超时能终止它，总预算也覆盖连接等待，但不替代独立的连接建立预算。**核实过的 SCG 4.3.5 契约**（旧实现引用的 `RouteMetadataUtils` 仍在 `spring-cloud-gateway-server`，不在 `-webflux`）：connect 侧经数字强制转换（下发 `Integer`），response 侧经 `getLong` 且**负值视为未配置**；connect 的窄化安全由 `TimeoutPolicy` 的取值上限保证（拒绝超过 `Integer.MAX_VALUE` 毫秒），不是一次可能静默截断的转换。另核实：**SCG 自身把响应超时映射为 `ResponseStatusException(504)`**，该超时与 Reactor 总预算超时均映射为 HTTP 504。
- SCG 的 `RequestRateLimiter`/`RedisRateLimiter` **不要叠加**：单一计数器，没有双槽位、跨阶段去重与稳定配额键，叠加会重复扣减。

验证（显式 Java 17，离线构建）：`mvn -o -B clean test` → BUILD SUCCESS，12 模块全绿，**389 项测试 0 失败 0 错误 0 跳过**（domain 148、application 79、infrastructure 47、interfaces 40，其余模块不变；较上轮 384 增 5 项）。`mvn -o -B -pl gateway-boot -am package -DskipTests` 与 `mvn -o -B -pl gateway-integration-tests -am test-compile -DskipTests` 通过；`git diff --check` 通过。集成测试模块仅编译，未运行真实中间件冒烟——**因此 connect/read 的实际生效只由 metadata 断言覆盖，未在真实 Netty 上验证过超时行为**。

### 适配器夹具、超时端到端验证与能力节点映射缺陷修复（2026-09-22 续二）

**真实缺陷（非测试问题）**：`ResolvedCapabilities.nodes()` 此前只映射 `RateLimitCapability.ID`，超时节点从未进入 `CapabilityNodes`。`TimeoutCapability` 与其执行器虽已在 `IngressWebFluxConfiguration` 注册，但 `CapabilityPlanner.unitsOf` 取到 `null` 后 `TimeoutCapability.units` 返回空列表，`FORWARD` 相恒不产生单元、下游调用不设预算——即「timeout 已生效」在运行链上不成立，只在领域单测自造的 `CapabilityNodes` 里成立。已改为**存在即登记**（`HashMap` + 条件 `put`，因 `CapabilityNodes` 拒绝 null 值；限流仍无条件登记——空规则组是真实答案，不是缺节点）。**教训**：能力注册（planner + executor）不等于能力可达，节点映射是第三个必须对齐的点。

**适配器夹具**（`IngressFiltersTest`）：`endpoint(route)` 拆为 `endpoint(route, requirement)` 并新增 `anonymousEndpoint`；新增 `preparedExchange(endpoint, snapshot)` 夹具写入 `PREPARED` 与 `PREPARATION_COMPLETE`——此前准入测试要么只调准备段，要么在内联构造后走被拒路径，**没有一条能走完准入进入 `FORWARD` 相**；`snapshot` 增加接收 composition 的重载与 `timeoutComposition()`（全局绑定；bundle id 与 binding id 刻意取不同值，两者是不同身份）。`planner()` 夹具把限流与超时一起登记，与运行期装配保持一致（漏登记执行器只在请求到达时才暴露）。

**新增测试**：无超时配置 → 不设预算、正常转发且无状态码（对照基线，否则无法区分「预算生效」与「全部被拒」）；配置超时且 chain 永不完成 → `GATEWAY_TIMEOUT`。领域层 `TimeoutCapabilityTest.shouldReachThePlannerThroughTheResolvedNodes` 钉住「resolver 把 timeout 节点交给 planner」这一配对。

**反向验证**：临时关闭该映射后 `shouldReachThePlannerThroughTheResolvedNodes` 失败（`expected: <1> but was: <0>`），确认新测试非空转，且缺陷定位正确。

**包布局漂移收口**：上一轮 `policy → capability` 的改名走了两跳（`policy/header/*` → `capability/singlevalue/header/*` → `capability/header/*`），**测试树只跟到第二跳**：`capability/singlevalue/{header,resilience}` 下 6 个测试文件仍声明 `package ...capability.plannable.singlevalue.*`（包名与目录均与主源码不符），并残留空的 `capability/plannable/` 测试目录。已把 6 个文件移到与主源码一致的 `capability/{header,resilience}` 并改正包名。**校验**：脚本比对全仓 425 个 Java 文件的包名与所在目录，不一致数 0。**边界**：变的是目录层级，`singlevalue` 仍是叶子、`plannable` 仍不引用 `composition` 这类**依赖方向**结论不变；`ResolvedCapabilities.nodes()` 的映射语义也不变。

本轮不改变 HTTP 路径、JSON 字段、错误码、快照格式或规范化摘要；仅新增测试夹具、新增测试、上述一处映射修复与一次测试目录／包名对齐。

验证（显式 Java 17，离线构建）：`mvn -o -B clean test` → BUILD SUCCESS，12 模块全绿，**333 项测试 0 失败 0 错误 0 跳过**（domain 119、application 74、interfaces 30；较上轮 330 增 3 项）。`mvn -o -B -pl gateway-boot -am package -DskipTests` 与 `mvn -o -B -pl gateway-integration-tests -am test-compile -DskipTests` 通过；`git diff --check` 通过。集成测试模块仅编译，未运行真实中间件冒烟。

### capability 包依赖收口（2026-09-22 续）

**契约类型归位**：`PlannableCapability` 移入 `plannable/`（与其两个子形态同处），`SingleValuedCapability` 移入 `singlevalue/`，家族根 `CapabilityDefinition` 留在 `capability/`。测试树同步对齐主源码布局——此前测试的包名（`capability.header` / `capability.ratelimit` / `capability.resilience`）在重排后已不存在，且 `CapabilityPlannerTest` 的包声明与目录不符。

**断开 `composition ↔ plannable` 包循环**：`PlannableCapability` 改为泛型 `PlannableCapability<N>`，`units(N node, CapabilityContext)` **只收自己的能力节点**；新增 `CapabilityNodes`（按能力标识索引的节点表，由 `composition` 侧通过 `ResolvedCapabilities.nodes()` 构造），`CapabilityPlanner.plan` 改收它。依赖方向因此单向：`composition → {plannable, singlevalue}`，`plannable` 不再触及 `composition`，`singlevalue` 仍是叶子。**收益**：plannable 能力不再被迫知道单值节点的存在，两个分支真正独立。

**为可测试性保留的取舍**：`CapabilityNodes` 是具体类而非接口——泛型抽象方法无法用 lambda 实现，且"只认识真实能力"的查找会让 planner 无法用能力替身测试。缺项返回 null，语义为"该能力本次无配置"，能力须按"无事可做"处理（`RateLimitCapability` 已如此）。一处未检查转换集中在 `CapabilityNodes.node`。

**连带**：`CapabilityExecutionCommandService` 改传 `resolved.nodes()`；`CapabilityExecutionCommandServiceTest` 中「断言 planner 收到与输出同一份节点」的用例改为断言**节点本身**（`assertSame(planned.get(), output.capabilities().rateLimits())`），比原先断言包装对象更精确。

**单值能力并入 plannable 家族（设计调整）**：此前 `SingleValuedCapability` 是与 `plannable` 并列的独立分支，且因 planner 只接受 `PlannableCapability` 而**没有执行路径**。现改为 `SingleValuedCapability<N> extends PlannableCapability<N>`——形态只约束"恰好一个单元、无门控"，不改变机制，因此单值能力同样由 planner 计划、由 `CapabilityExecutor` 执行。

理由：单值能力需要与其它形态完全相同的执行机制（上下文、事实、单元、结局、去重键），唯一区别是没有可选项；为它们另开一条执行路径要多一份契约和一个消费者，而 planner 已经够用。**代价**：放弃"单值不进 planner"这条早先的设计。

**包布局随之收敛**：`singlevalue/` 由 `capability/` 下的并列分支降为 `plannable/singlevalue/`，`SingleValuedCapability` 与另两个形态同置于 `plannable/` 下。最终布局——`CapabilityDefinition`（根）/ `composition/` / `plannable/`（唯一执行家族：三个形态接口 + `execution/` + `ratelimit/` + `singlevalue/`）。DOMAIN §2.6 的「哪些能力进 planner」判据表相应改为「形态只描述单元从哪来」。

验证（显式 Java 17，离线构建）：`mvn -o -B clean test` → BUILD SUCCESS，12 模块全绿，326 项测试 0 失败 0 错误。

### 部署键改名收口与管理路径漂移修复（2026-09-22）

**部署键改名收口**：`policies → capabilities/access` 的更名此前只做了一半——生产默认值与部分测试已改用新名，示例文件、README 与另两处测试资源路径仍是旧名。本轮补齐：示例文件改为 `gateway-access.yaml`、`gateway-app-capabilities.yaml`、`gateway-client-capabilities.yaml`、`gateway-access-configuration.yaml`（`gateway-endpoints.yaml` 不变）；README 同步文件名、配置前缀（`gateway.policy-definition` / `gateway.policy.nacos` → `gateway.access-definition` / `gateway.access.nacos`），并把「不变更部署配置键或 dataId」的过期表述改为实际发生的更名清单。

**管理路径漂移修复（真实缺陷）**：示例与测试中的管理路径被改名 sweep 误改为 `/admin/gateway/capabilities/...`，而权威来源 `GatewayPathApiConstants` 与真实控制器用的是 `/admin/gateway/policies/...`。两侧同改导致测试反而通过，示例则教了一个不存在的路径。已把 2 份示例与测试中的 16 处路径改回 `/policies/...`。**根因无法自动防护**：`gateway-api` 是外层契约，本模块不得依赖（`LayerDependencyTest` 同精神），路径字面量只能手工与常量保持一致——已在两处测试注释中标明这一限制与历史。

**摘要重算**：路径修正后按现有规范化编码重算示例摘要为 `ced1e486ad3b3b142e255e225b7c7c6176a4d909333ee0dd81bb769df9fc7679`（原 `f10716f0...` 对应路径正确但规范化形式未变时的内容；本轮规范化形式亦已变化）。未放宽摘要校验。固定向量测试中的路径篡改断言此前退化为 `replace(A, A)` 空操作（改名 sweep 把两侧改成同一值），已恢复为真正篡改并加注释说明「两侧必须不同，否则摘要守卫从未被执行」。

验证（显式 Java 17，离线构建）：`mvn -o -B clean test` → BUILD SUCCESS，12 模块全绿，326 项测试 0 失败 0 错误。集成测试模块编译通过但未运行。

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
- 本期只登记限流；未知／歧义路径的全局 IP 保护、单请求单快照、签名、Redis 键及快照格式不变。身份绑定 IP 规则在 POST_AUTH 首次执行；不增加文档或模块依赖。

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

**待决（已解决）**：本条曾记录 `CapabilityResolver` 的调用点位于接入段、与 DOMAIN 第 1.1/1.3/4.3 节的「准入段」不一致。现已按文档对齐：能力解析由 `CapabilityExecutionCommandService` 在准入段完成，接入段只产出判定与 `Prepared`，DOMAIN §4.3 亦已同步。

### 标识值对象迁移与遗留缺陷修复（2026-09-16）

本轮完成 `Revision` 与 `SnapshotVersion` 值对象在 domain/application/infrastructure 的贯通，并修复此前遗留的五项缺陷。

标识：`Revision(String)` 表示源修订令牌，`SnapshotVersion(long)` 表示正数快照版本（取代原 `AccessSnapshotVersion`）。`RevisionedAccessDefinition`、`RevisionedAccessSnapshot`、`IngressSnapshot.version()`、`WritableAccessDefinitionRepository.compareAndSet`、`AccessSnapshotPublisher.compareAndSet`、`AccessSnapshotHistory.find`、`RollbackAccessSnapshotCommand` 与 editing 层全部命令服务均使用这两个值对象。**边界**：`gateway-api` 的 DTO 保持 `String sourceRevision` 与 `long snapshotVersion` 作为线上协议，转换只在 `gateway-interfaces` 门面内进行。`AccessSnapshotAssembler.assemble` 收 `SnapshotVersion`，版本递增由 `assembleUpdate` 内部完成，调用方不再计算版本；至此应用层与基础设施层不存在裸 `long`/`String` 标识。

缺陷修复：LKG 文件存储改为循环写 + 写后 force + 移动后 fsync 目录，原子移动不受支持时降级为「先复制再 force 目标」；限流适配器新增 `gateway.ratelimit.redis.acquire-timeout`（默认 500ms），以有界线程池 + 超时界定阻塞调用，超时与池满降级为 `unavailable` 交给 RedisFailurePolicy，同时保证非依赖类异常原样抛出；`NacosAccessSnapshotStore.compareAndSet` 在远端 CAS 成功后不再因本地投影失败而向外报错，避免调用方重发；`NacosAccessSnapshotHistory.find` 由引用比较改为值比较。

错误归属：`PolicyYamlCodec` 只报自身层级的 `INFRA_TECH_POLICY_DESERIALIZATION_FAILED`，不区分失败原因；三个 decode 调用点（`NacosAccessSnapshotStore.accept`、`AbstractNacosAccessDefinitionRepository.load`、`NacosAccessSnapshotHistory.find`）各自按语境转换为 `INFRA_ACL_NACOS_CONTENT_INVALID`，领域校验原因作为 cause 保留在堆栈中。基础设施层不重新分类领域异常。

端口命名对齐：`AccessSnapshotProvider.current()` 更名为 `AccessSnapshotCapturer.capture()`，与 DOMAIN.md 第 1.3 节的能力名 `snapshotCapture` 及第 4.1 节准备段第一步同名；捕获现有版本、不创建新版本（创建归 `AccessSnapshotPublisher`），每请求恰好捕获一次。实现类与测试类同步更名为 `PropertiesAccessSnapshotCapturer` / `PropertiesAccessSnapshotCapturerTest`；`NacosAccessSnapshotStore` 同时实现捕获与发布两个端口，名称不变。

**破坏性变更**：规范化内容摘要再次变化，canonical contentHash 为 `fd603a9c3e085bc97c193b2ddf641320c39951cda77f5b64684046399e212a4a`；本地只读配置键由 `gateway.policy-definition` 更名为 `gateway.access-definition`。外部开发快照与本地配置必须同步更新。

验证（显式 Java 17，离线构建）：`mvn -o -B clean test` → BUILD SUCCESS，12 模块全绿，284 项测试 0 失败（domain 106、application 60、infrastructure 35、nacos 21、coordination-redis 3、security-spring 29、replay-redis 4、ratelimit-redis 18、interfaces 8）。集成测试模块编译通过但未运行。

**已知欠账**：gateway-integration-tests 仅完成编译适配，七个场景本轮均未在真实基础设施上运行（Docker 未使用）。

### 在途重构收尾（2026-09-15）

本轮修完上一次重命名重构留下的全部编译阻断。

请求链语义：准备段写入完成标记与已判定匹配结果，接入段消费该结果而不再重新推导；`CapabilityComposition.validate` 接入 `AccessSnapshotAssembler`，发布前校验绑定引用与同层竞争；管理查询端点登记 GET matcher 并由 `ManagementAuthorizationManager` 校验 Host 会话声明；歧义匹配补上 `gateway.ingress.ambiguous_endpoint_match` 低基数指标。

模型对齐：端点接入状态并入端点 Definition（`access` 节点），凭据转发改由 HeaderPolicy 承载；infrastructure 层从 `Map<Id, XAccess>` 端口到 registry 模型；`RateLimitKeyResolver`、`BrowserSessionVerifierAdapter`、`Credential`/`ClientAccess`/`Endpoint` 构造点全部对齐当前领域类型。

验证（显式 Java 17，离线构建）：`mvn -o -B clean test` 下 gateway-domain 106、gateway-application 60、gateway-infrastructure 35、gateway-infrastructure-persistence-nacos 21、gateway-infrastructure-coordination-redis 3、gateway-infrastructure-security-spring 29、gateway-infrastructure-replay-redis 4、gateway-infrastructure-ratelimit-redis 17、gateway-interfaces 8 项测试通过，失败与错误均为 0。示例资源按新 schema 重新生成，canonical contentHash 变更为 `43c00446c56e49ab1a72f5f391c6c528a772b047ad51039b15de406d02b9735f`，外部开发快照必须重新生成。

**已知欠账**：gateway-integration-tests 的七个场景尚未对齐新管理面协议与新端点模型，该模块测试编译未通过，`mvn verify` 仍不可用；本轮未运行任何真实基础设施冒烟（Docker 未使用），因此上述数字只代表单元／模块契约层。另需注意：本地 `~/.m2` 中存在 2026-09-08 安装的 `0.3.0-SNAPSHOT` 陈旧构件，`mvn -pl <模块>` 不带 `-am` 会对其编译，所有局部验证必须带 `-am` 或从根工程执行。

### 两阶段模型与管理面协议文档同步（2026-09-13）

本轮全面重写 DOMAIN.md（两阶段生命周期、三段式入口准入、能力库与固定拓扑）与本文件职责/交付表述；管理面接口定稿为四个门面（IngressAccessDefinition/Snapshot × Command/Query，content-only 保存与独立状态转换，文件级 sourceRevision CAS 语义）。

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

回归覆盖当前时刻已过期的历史回退在写入前拒绝、发布成功后凭据过期仍能完成阻断清除、App 平台不变约束、限流全规则执行及统一聚合、未知／歧义路径 IP 保护、准备阶段绕过拒绝、认证及配额取消、Header 校验、Facade 必需参数与生成映射。真实生产 SecurityWebFilterChain 在内存上下文执行，覆盖 JWT／Reference 两种验证适配下的管理 401／403／放行及非 Host 证据选择；没有真实端口或中间件。

本轮未运行真实基础设施冒烟，暂缓的下游 Header 迁移及完整协作验收状态不变。

### 前轮开发验证（2026-09-09，职责复查前）

显式 Java 17：

- `mvn -pl gateway-application,gateway-infrastructure-coordination-redis -am test -q` 通过。
- `mvn clean test -q`：307 项单元／模块契约测试通过，失败、错误、跳过均为 0。
- `mvn -pl gateway-boot -am package -DskipTests -q`：启动包构建通过；此命令不执行测试。
- 静态检查：生产代码无 var、控制流缺失大括号为 0，domain/application 无 Spring/Reactor 或外层实现 import，`git diff --check` 通过。

新增验证覆盖回退前置失败和原有阻断保留、单快照认证及维度解析、延迟认证／取消传播。已有序列化固定向量、Header 准入及签名测试继续通过。开发阶段未运行 smoke；既有集成测试只做类型／构造器编译适配，不视为本轮真实基础设施验收。

### 历史冒烟与未完成协作验收

2026-09-08 最近一次 GatewayPipelineIT 的 5 项真实请求链测试通过；这是历史结果，本轮未重跑。历史完整协作场景最近结果为 27 项通过、1 项失败：BrowserSessionIngressIT 的现有 user-auth 运行包在 `/api/challenges` 未按新 Header 契约恢复 clientAppId/channelCode，必填校验失败。下游 Header 消费迁移仍按用户决定暂缓，未恢复旧 Header 别名，完整协作验收尚未通过。

上述历史结果不替代本轮 smoke。进入冒烟阶段后需复验真实请求链、Nacos 发布／回退和 Redis 协调，再单独评估暂缓的 Browser Session 协作。

- 外部不能绕过入口；未知端点和实际路由不一致拒绝。
- 伪造身份 Header 清除；未验证 Client 不能匹配可信身份绑定。
- Bundle 修改仅发布后生效；一个请求只用一个快照；非法配置保留 LKG。
- 配额按稳定规则执行，热更新不重获满额；Redis 故障语义可验证。
- 下游超时与响应提交语义正确。
- 历史签名固定向量不变，新签名覆盖字段篡改和跨实例重放被拒绝。
- 日志不包含 Token、Cookie、secret 或完整载荷。
