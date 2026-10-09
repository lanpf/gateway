# gateway 专属技术设计

本文维护值得长期保留的 gateway 实现决策，不定义业务规则、协议格式或交付状态。分别参见 [DOMAIN](DOMAIN.md)、[PROTOCOL](PROTOCOL.md) 和 [RESPONSIBILITIES](RESPONSIBILITIES.md)，部署入口见 [README](../README.md)。

通用约定直接遵循 [分层服务 BP](../../engineering-guidance-publisher/standards/bp_layered_service.md)、[Java BP](../../engineering-guidance-publisher/standards/bp_java.md)、[基础工具与 MapStruct BP](../../engineering-guidance-publisher/standards/bp_common_tools.md)、[错误码 BP](../../engineering-guidance-publisher/standards/bp_error_codes.md) 及项目安装的 [develop-service](../.agents/skills/develop-service/SKILL.md)。本文只说明本项目的补充与取舍。

## 能力目录与校验复用

配置组合模型位于 domain.capability.policy，执行模型位于 domain.capability.runtime，两者互不引用；能力自己的纯配置与规则放在 ratelimit/resilience/header。catalog 固定登记内建能力，通用容器不反向依赖目录。application.ingress 连接本次生效策略与运行能力。目录由部署代码提供，不随快照变化，不使用动态插件发现、脚本或配置类名。

BuiltInCapabilityCatalog 统一声明 HEADER_APPLY、RATE_LIMIT、TIMEOUT 标识。CapabilityId 保持开放，扩展能力声明稳定 ID。Config、ConfigRule、Composer、PlannableCapability 各自声明身份，不增加共享身份父接口；通过注册检查保证一致。

装配从同一 Composer 集合创建共享 CapabilityPolicyConfigValidator，再注入 CapabilityPolicyAssembler。Validator 持有固定 ID/配置类型元数据、专属规则和累加能力集合，不保存或调用 Composer；Assembler 仅负责可信事实匹配、排序、Bundle 去重和组合，不创建或对外转发 Validator，也不提供 Composer 查询。Assembler 构造时检查目录标识和配置类型完全一致。

保存、发布、Definition/Snapshot 编解码直接调用该 Validator；请求组装在 Assembler 内复用它。内容与完整校验的业务范围以 [DOMAIN 快照规则](DOMAIN.md#7-快照与发布)为准，完整快照编码及 Nacos/LKG 解码都不能仅验证摘要。能力专属 ConfigRule 只接收自身能力的完整配置列表，不感知 Bundle/Binding 或 Composer；列表无 null、不可变且不表示优先级，没有配置时传空列表，无额外规则的能力不增加空实现。

Composer 只声明身份、配置/策略类型并组合有序配置，不承担全量校验或同层竞争。Bundle 的身份/类型检查由 CapabilityConfig 契约复用，不依赖 Assembler。各能力自身配置保护局部不变量，跨配置与部署准入由专属规则承担，不读取整个 PolicyConfig 或可变部署 Properties。

### 校验缓存

固定规则的 Validator 仅弱引用记忆最近一次成功校验的不可变 PolicyConfig **对象身份**。命中不计算内容 hashCode、不比较内容相等性，也不使用共享锁；同内容的新实例仍须校验。被回收或被新成功实例替换后重新校验。并发未命中允许重复执行纯校验，失败不缓存，引用及同层竞争检查每次执行。缓存不能改变接受/拒绝语义，规则变化不能复用旧规则的记忆。

### 请求级规划与扩展

EffectiveCapabilityPolicies 按精确声明的策略 Class 类型读取，不扫描继承关系；每种能力有独立策略类型，缺失、重复类型、null 或错误类型产出拒绝。不增加标记接口、Key 或 Registry。它只保存策略，不持有 Composer 或执行记录。

CapabilityRegistration 配对 PlannableCapability 与策略读取函数，每次调用生成直接持有非空策略的 CapabilityPlanningEntry。Planner 无状态，不引用 EffectiveCapabilityPolicies；不缓存请求 Entry，不用 Supplier、ThreadLocal 或共享字段隐式捕获请求配置。未配置状态由具体策略表达，不用空 Entry 跳过安全能力。配置数量、单元数量和槽位数量独立，不分单/多槽位继承体系。

CapabilityExecutionCommandService 统一协调解析、规划和批次执行。构造时双向检查能力与执行器标识，生产装配检查配置/运行目录一致；编解码目录按 ID/配置类型完整覆盖配置目录，不要求 Composer 实例相同。逐请求在首个副作用前检查完整计划及执行器/单元类型。

新增能力提供配置、生效策略、组合器、运行定义/执行器、必要的 ConfigRule 和受控编解码，不修改通用 Bundle、Assembler、Planner 或策略容器。扩展能力不能绕过领域固定安全时机，见 [能力执行规则](DOMAIN.md#53-能力执行契约)。

## 入口接入与协议适配

IngressPreparationWebFilter 在安全链和 SCG 路由分发前捕获 RequestEvidence，调用准备用例；完成标记防止重入。IngressAdmissionGlobalFilter 消费该 Prepared，不重新选端点、读取快照或补空执行记录；实际 SCG route id 必须与捕获端点 routeId 相等，本地管理路由使用 gateway-admin 保留标识。代理与本地入口复用准入适配，不用三个过滤器强行对应三个业务阶段。

应用阶段使用领域事实与 CompletionStage，避免依赖 Spring Security/Reactor。IngressAdmissionCommandService 协调接入后准入；阶段之间不直接持有彼此，拒绝只交终态。IngressTask 传播取消和异步失败。签名证据按匹配策略采集，保留原始 body 并正确释放 DataBuffer；证据格式见 PROTOCOL。

PRE_AUTH/POST_AUTH 各按可信事实解析，FORWARD 用 executeAgainst 复用准入段生效策略。HeaderApplyCapability 产出计划，适配器在发起下游前应用；计划缺失关闭请求。领域 Header 默认与可信来源见 DOMAIN，不能从 HTTP 声明补齐可信字段。

### SCG 超时

RouteTimeoutMetadata 将 connect/read 写入命中路由 metadata：connect-timeout 与 response-timeout，单位毫秒；重建路由保留原 metadata 和身份。connect 以 Integer 交给 Netty，response 以 long 读取，负值视为未配置。TimeoutConfig 的每项预算范围为 1 到 Integer.MAX_VALUE 毫秒，窄化前由配置有效性保证范围。

total 经 Reactor timeout 包裹整条下游调用 Publisher；预算的业务范围见 [超时规则](DOMAIN.md#51-超时)。SCG 响应超时可产生 ResponseStatusException(504)，适配器仅翻译已识别异常；对外映射及提交后处理见职责文档。SCG RequestRateLimiter/RedisRateLimiter 不与本限流链叠加，避免重复扣减。

## 配置转换与条件装配

接口层复用基础 infrastructure 的 CapabilityConfigCodecs，不维护第二份 HTTP 能力目录，也不依赖 Nacos 具体仓储。此为单向外层协议编解码复用；API/domain/application 不反向引用该组件。开放协议树进入领域前按登记类型严格解码；合法报文、未知节点和规范化编码的要求以 PROTOCOL 为准。

管理 HTTP、Facade、Properties 和存储结构投影各有独立转换契约；普通字段映射遵循共享 MapStruct BP，领域重建、授权、状态变更及签名编码保持显式语义。Stored 重建经领域不变量，Canonical 编码保留排序、格式、时间及空值语义，不用自动字段映射代替规范化。两外层模块复用各自严格 mapper 配置，不为复用引入反向依赖。

Definition 写请求通过 ClientRequestBodyArgumentResolver 读取 body、覆盖可信上下文后校验，避免标准 RequestBody 提前截获。写 Controller 按可写用例条件装配，并从 boot 扫描排除；只读部署保留查询用例。WebFlux 绑定与 Spring 代理校验失败由 GatewayValidationExceptionHandler 翻译为 framework OBJECT_INVALID，Facade 接口约束通过代理执行。当前入口验收限制见职责文档，不以这些设计保证所有 HTTP 端点已可达。

## 发布与恢复执行保护

领域保存/发布/回退的决策与检查点见 [DOMAIN](DOMAIN.md#7-快照与发布)。应用以 AccessDefinitions 和单一 AccessSnapshotPublication CAS 边界协调发布/恢复；保存共用 AccessDefinitionWriter 的阻断、修订和 CAS 检查，不采用继承 CRUD 模板。

发布持有 publish 锁，回退固定按 rollback → publish 获取立即失败的锁。历史查找和目标摘要预计算在锁外；锁内重读状态与 revision。Definition 逐份 CAS，操作预算检查包围各边界；持久 rollbackState 在 Redis 无 TTL，不能依赖进程内状态替代。对运维调用方的失败/重试责任见职责文档。

gateway.access.operations.timeout 默认 60 秒，max-blocking-time 默认 10 秒，lease-safety-margin 默认 20 秒。framework.lock.redis.lease-time 必须覆盖三者之和，默认组合至少 90 秒；启用自动续租也检查。配置样例租约为 90 秒，Nacos config-request-timeout=5000。Nacos SDK 读写单次阻塞上界须符合 max-blocking-time；Redis 命令及连接超时必须为正且合计不超过该上界，样例为 PT5S/PT2S。装配拒绝不满足保护关系的配置。

预算是协作式检查，到期不开始下一次读写；执行中的同步 SDK 等其有界超时返回，额外阻塞受 max-blocking-time 限制。Nacos 3.0.3 可将传输失败转为 false，SDK 超时/false 或取消 Future 不表示远端写入停止。原 revision CAS 防止迟到写覆盖已前进状态；未知结果以失败处理，重试先读当前状态，不能据此清除阻断。

历史查询受 history.query-timeout（默认 60 秒）、page-size（1–200）、max-pages（1–100）限制；每次 REST 返回和下一条详情请求前查预算。connect/read timeout 由 history.rest-client 控制，历史查找不占发布/回退临界区。这些参数是执行保护，业务 SLA 由部署维护方确定。

## 运行快照恢复与观测

Nacos 监听、定时刷新和 LKG 重试属于技术恢复。接纳后原子切换运行快照；低版本通知按当前版本门控，版本接纳判定复用 DOMAIN。LKG 以完整写入、force 和目录同步保留最后有效快照；原子移动不支持时采用复制后 force 目标的退化路径。LKG 写失败时，即使远端仍是同一快照，也重试落盘，陈旧重试不能覆盖较新版本。远端 CAS 成功后的本地投影失败不伪装成远端 CAS 失败。

Redis EXACT 令牌桶原子执行共享额度；acquire-timeout 默认 500ms，有界线程池和超时将依赖不可用交回用例按领域故障方式处理，非依赖异常原样传播。本地应急额度有界，具体保证见职责文档。remoteIp 当前取 remote address，X-Forwarded-For 的信任由部署侧管理。

有 MeterRegistry 时采集固定、低基数标签，不带调用方、端点或密钥：

- gateway.ingress.requests、gateway.ingress.ambiguous_endpoint_match、gateway.quota.decisions。
- gateway.access.refresh.failures、gateway.access.refresh.last_success_seconds、gateway.access.refresh.degraded、gateway.access.lkg.failures、gateway.access.lkg.degraded、gateway.access.stale_notifications。
- gateway.browser_session.verification.failures、gateway.browser_session.verification.last_success_seconds、gateway.browser_session.verification.degraded。

刷新/LKG/Browser 验证失败门控为进入故障 WARN、恢复 INFO；持续恢复失败按一分钟窗口汇总，不逐请求限流输出 INFO。日志只记结果和异常类型，不携带配置、Token 或响应载荷。操作摘要记录目标版本、已完成 Definition 数、耗时与结果；日志留存和告警交付由部署维护方负责，见职责文档。

## 技术错误归属

702（密钥缺失）由 security-spring 的 SecuritySpringError 持有；705（回退状态失败）由 coordination-redis 的 CoordinationRedisError 持有；802（Nacos 依赖不可用）由 persistence-nacos 的 PersistenceNacosError 持有。803（Nacos 内容非法）保留基础 infrastructure，共享编解码与具体存储共同引用，基础模块不反向依赖具体存储。800/801 的既有编号没有抛出点仍保留，不重排或复用。

技术参数、集合元素及注册类型异常使用技术错误，不能扩大业务错误面；稳定业务号段与判定见 DOMAIN。外部写入未知结果按操作语境保留失败责任，不能通过技术异常翻译改变业务规则。技术枚举和完整模板以各所属模块的发布定义为准。
