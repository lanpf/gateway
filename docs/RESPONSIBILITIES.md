# gateway 职责与交付 · v1

本文是服务边界、跨服务协作、对外保证和交付限制的权威来源。领域判定见 [DOMAIN](DOMAIN.md)，签名与配置表示见 [PROTOCOL](PROTOCOL.md)，专属实现决策见 [DESIGN](DESIGN.md)，运行与验证入口见 [README](../README.md)。

## 职责边界

gateway 将外部不可信请求转为受控入口请求，负责身份验证、接入控制、签名防重放、配额与转发治理；各阶段决策以 DOMAIN 为准。Client 承接原业务渠道身份，不增加渠道映射。

| gateway 不负责 | 所有者 |
| --- | --- |
| 账户、登录会话生命周期、Token 签发/撤销、Role/Permission | user-auth |
| 资源归属和最终业务授权 | 拥有对应资源的业务服务 |
| 共享自定义 Header 名称契约 | framework |
| 网络隔离、代理来源可信配置、指标导出、告警送达与日志采集/留存 | 部署维护方 |
| 受管理 Definition 身份提供、冲突处理、环境配置初始化 | 配置维护方 |

入口放行不替代最终业务授权。gateway 不查询业务资源、不签发 Token；认证与配额失败不由下游业务服务补救。

## 协作契约

### 外部客户端与下游服务

客户端以 v1 协议声明 App/Client 并提交所需认证证据，报文见 PROTOCOL。gateway 对原始事实作入口判定，获准后按领域安全计划清除和写入上下文，再转发到登记路由。拒绝不触发下游业务调用。客户端版本是声明，不参与授权；身份可信性以 [领域规则](DOMAIN.md#27-跨模型规则)为准。

下游只接受 gateway 和授权内部工作负载的网络访问，不直接暴露公网。内部调用仍使用可信内网，不增加 workload identity/delegation；Header 本身不是穿越不可信网络的身份凭据。下游拥有最终业务授权，不能把 gateway 的入口身份或 App platform 等属性当作最终资源授权。

### framework 与共享 Header

共享名称由 [framework 职责文档](../../framework/docs/RESPONSIBILITIES.md#framework-core)拥有；gateway 拥有可信取值、清除、字段选择与转发语义，见 [DOMAIN Header 规则](DOMAIN.md#6-header-与信任上下文)。下游消费逻辑由 framework-starter 和相应服务各自迁移，枚举名称更新不自动证明消费侧兼容。

gateway 不登记历史别名、LEGACY 分类或兼容映射，不专门识别/清除历史名称。user-auth/framework-starter 的 Header 消费迁移仍暂缓；现有下游完成迁移后才能按当前契约协作。历史签名计算继续兼容，与 Header 历史名称无关。

### user-auth 协作

Host Session 的签发、撤销及 JWT/introspection 协议由 user-auth 拥有；gateway 验证 Token 后恢复主体事实，接入要求见 DOMAIN。外部 `/api/user-auth/**`、`/admin/user-auth/**` 剥离服务段转发；`/oauth2/**`、`/.well-known/**` 原样转发。`/internal/**` 不登记、不公开路由。

Browser Session 的调用方流程由 gateway 维护：

1. 从请求传输证据取得 Browser Cookie；由接入过程决定是否触发验证，未知/歧义路径不触发。
2. 经受管理 serviceId 的内部实例，使用 user-auth-api 发布的路径和载荷请求在线验证。
3. 接纳响应或分类无效、不可达、非法响应，再按 DOMAIN 进行接入与 Origin 判定；失败不转换为匿名、宿主或缓存身份。
4. 获准后按策略向 user-auth 转发可信上下文及允许的原始 Cookie，响应 Cookie 由 user-auth 拥有，gateway 不刷新 Cookie。

user-auth 持有在线验证端点、会话状态、idle TTL、绝对期限和父会话规则；其完整载荷和消费语义只在 [user-auth 协作契约](../../user-auth/docs/RESPONSIBILITIES.md#browser-session-在线验证端点)维护。gateway 保持返回的 sessionId 身份，不把剩余 idle TTL 当作不会提前撤销的保证。会话结束端点也以 [user-auth 端点契约](../../user-auth/docs/RESPONSIBILITIES.md#browser-session-结束端点契约)为准，不在本工程复制表或消费规则。

### 管理调用方

管理调用需通过 App 接入与管理 Host 身份要求，业务最低授权见 [DOMAIN 管理链](DOMAIN.md#44-管理入口链)。body 的身份声明不能证明权限。管理协议分 Definition/Snapshot、命令/查询四类；正式载荷复用 [gateway-api](../gateway-api/src/main/java/com/cloud/gateway/api/facade/admin)，配置表示和示例见 PROTOCOL。

| 端点/操作 | 对调用方的契约 |
| --- | --- |
| PUT /admin/gateway/policies/{app,client-access,endpoint-access} | 保存单条接入内容，返回新 sourceRevision；创建/编辑的状态及凭据保留规则见 DOMAIN。 |
| PUT /admin/gateway/policies/client-credential | 整体轮换已有 Client 的凭据，返回新 sourceRevision；与接入编辑共享文件修订。 |
| PUT /admin/gateway/policies/{app,client-access,endpoint-access}/status | 单独切换状态，返回新 sourceRevision。 |
| PUT /admin/gateway/policies/configuration | 整份保存能力组合，不自动发布；未携带条目从编辑态移除。 |
| Definition 查询 | 返回集合内容与共享 sourceRevision；未初始化时修订为空，不回显 secretRef。 |
| POST /admin/gateway/snapshot/publish | 显式发布，内部观察全部 Definition；不要求调用方发布令牌。 |
| POST /admin/gateway/snapshot/rollback | 指定正数历史版本，恢复后返回快照事实。 |
| Snapshot 查询 | 返回当前发布事实；从未发布时字段为空。 |

具体路径常量见 [管理路径契约](../gateway-api/src/main/java/com/cloud/gateway/api/constants/GatewayPathApiConstants.java)。本表声明协议意图，**不保证所有 HTTP 入口已可达**；当前验证范围见交付状态。查询与发布/回退的用例和 Facade 存在不能替代 HTTP 可达性验收。

只读部署不提供保存、发布、回退写入口，保留查询用例。保存的文件级 CAS、发布闸门和恢复结果以 [DOMAIN 管理用例](DOMAIN.md#8-管理用例)为准；新 revision 是连续编辑令牌，不是新发布版本。

## 对外保证与限制

### HTTP 失败映射

领域拒绝先按 DOMAIN 判定，适配器再映射 HTTP；状态码不反向定义领域原因。下表记录现有入口投影及其明确限制。

| 结局 | HTTP 表示 |
| --- | --- |
| APP_NOT_FOUND、CLIENT_REQUIRED | 400 |
| ENDPOINT_NOT_FOUND；实际路由与端点 routeId 不一致；准备事实缺失 | 404，未知与歧义端点对外统一 |
| CLIENT_VERIFICATION_REQUIRED、CLIENT_VERIFICATION_MISMATCH、SUBJECT_REQUIRED、SUBJECT_AUTHENTICATION_REJECTED | 401 |
| CLIENT_NOT_FOUND、CLIENT_NOT_ALLOWED、CLIENT_VERIFICATION_REJECTED、AUDIENCE_NOT_ALLOWED、SCOPE_NOT_ALLOWED、BROWSER_ORIGIN_NOT_ALLOWED | 403 |
| CLIENT_VERIFICATION_FAILED、SUBJECT_AUTHENTICATION_UPSTREAM_UNAVAILABLE | 503 |
| SUBJECT_AUTHENTICATION_RESPONSE_INVALID | 502 |
| Browser Session 无效 / 不可达 / 非法响应 | 分别 401 / 503 / 502 |
| 证据 body 超过协议上限 | 413 |
| 配额超限 | 429，Retry-After 为最大等待时间向上取整秒，至少 1 秒 |
| 配额依赖不可用且没有允许的应急结果 | 503 |
| 下游已识别的连接失败 / 超时 | 分别 502 / 504，响应未提交时映射 |
| 缺失必需 Header 转发计划 | 500，失败关闭 |

整理前签名段笼统约定“凭据缺失或错误返回 401”，但现有签名 Rejected 进入 CLIENT_VERIFICATION_REJECTED 后映射为 403。该原有 401 约定与当前行为的偏差尚未完成契约对齐；上表说明当前投影，预期契约及修复范围仍需确定。

响应已提交时不替换原响应或标准错误体；未分类异常继续传播。业务错误码不通过消息文本识别；管理绑定与校验失败使用既有 framework OBJECT_INVALID，业务冲突保留所属稳定错误。业务号段见 DOMAIN。

### 发布、恢复与运行约束

请求内保持完整单一快照；不同实例可短暂持有相邻版本，不保证全局瞬时撤销。编辑态保存不影响运行请求，显式发布是生效闸门。未知能力或非法快照拒绝加载；无有效远端快照和 LKG 的首次启动失败。

发布/回退的成功输出不是所有实例已切换的保证。超时、SDK false 或取消不证明远端没有提交；调用方失败后先检查 Snapshot 与各 Definition revision，再按当前状态重试。回退部分失败或清除失败时保留阻断，不人工删除以绕过恢复；同目标重试复用检查点，无法自动恢复时由配置维护方处理冲突。业务 SLA 由部署维护方确定，执行预算及租约参数见 DESIGN。

EXACT 依赖共享额度；OPEN_WITH_LOCAL 只提供有界本地应急，不保证集群精确，需要部署告警。管理最低要求在启动校验当前快照，运行期独立执行；启动检查不代表每次发布已完成管理端点专项审查。

Header 策略随快照动态更新；部署否决名单变更当前需重启，不提供独立规则热刷新。部署规则和策略必须联合有效，不能在请求中混用未经校验的组合。只允许快照发布身份写 Snapshot，运行身份只读；仓库不自动升级或覆盖外部 Nacos/Redis 数据。

### 部署与故障处置责任

部署维护方负责限制下游和 internal 网络访问、代理来源可信设置、配置指标导出和告警送达。具体指标名见 [观测设计](DESIGN.md#运行快照恢复与观测)，不以日志代替告警。

刷新持续失败超过两个 recovery-interval、LKG 故障持续、回退阻断未清除或最后成功时间超出允许恢复窗口时应告警。部署日志留存须覆盖至少一次完整故障/恢复周期并满足生产审计要求。gateway 的操作摘要提供目标版本、恢复进度、耗时和结果，不输出 Token、Cookie、密钥或完整载荷。

## 交付状态

以下区分现有实现、最近有记录的验证范围和未完成承诺。领域设计成立不代表交付；历史验证记录不能证明当前工作区通过。

| 能力 | 现有实现及验证边界 | 限制/证据 |
| --- | --- | --- |
| 入口准入、EXACT、超时与 Header 应用 | 已有固定管线及运行实现；最近有记录的真实请求链和 Redis 场景通过。 | [GatewayPipelineIT](../gateway-integration-tests/docs/GatewayPipelineIT.md)、[RateLimitIT](../gateway-integration-tests/docs/RateLimitIT.md)说明场景；执行结果来源见下文。 |
| Definition 保存与完整快照校验 | 能力组合 HTTP 保存、Facade、应用和编解码协作已有验证。 | [管理协作场景](../gateway-integration-tests/docs/CapabilityPolicyConfigManagementIT.md)、[能力装配场景](../gateway-integration-tests/docs/CapabilityAssemblyIT.md)。 |
| Nacos 发布/回退、传播、Redis 协调与重放 | 最近有记录的隔离基础设施场景通过，包含部分恢复和重复触发。 | [Nacos 管理](../gateway-integration-tests/docs/NacosPolicyAdministrationIT.md)、[快照传播](../gateway-integration-tests/docs/AccessSnapshotPropagationIT.md)、[协调](../gateway-integration-tests/docs/RedisPolicyCoordinationIT.md)、[重放](../gateway-integration-tests/docs/RedisReplayGuardIT.md)。 |
| 管理查询、发布、回退 HTTP 入口 | 用例和 Facade 已实现，HTTP 可达性尚未验收。 | 三个管理查询/快照写入口存在注册缺口；不能用注解形状或用例测试代替可达性证据。 |
| ANONYMOUS 接纳可信 EXPIRED | 领域有该规则，现有认证入口未提供“其余验证可信且仅过期”的独立分类。 | 不声称真实请求链已支持过期匿名放行，也不放宽 INVALID 拒绝。 |
| Browser Session 完整协作 | 无效 Cookie 拒绝已有验证；有效 Cookie 会话结束和非法 Origin 验收未完成。 | [BrowserSessionIngressIT](../gateway-integration-tests/docs/BrowserSessionIngressIT.md)受 user-auth 会话签发前置失败阻碍。 |
| Header 消费互操作 | gateway 输出侧已有实现，user-auth/framework-starter 消费迁移暂缓。 | 完整跨服务验收未通过，不恢复历史 Header 别名。 |
| 签名拒绝 HTTP 对齐 | 原有 401 约定与当前 403 投影有偏差。 | 见 HTTP 失败映射；契约决策及代码修复另行处理。 |

PREALLOCATED、动态插件、任意脚本、最终业务权限判定、全局瞬时撤销不属当前交付。authenticationExpiresAt 暂无后续治理消费者，不增加到期前不会撤销的承诺。

相邻 framework 职责文档仍使用 wireName/HeaderCategory/PROTOCOL 描述，本地 Header 源码使用 headerName/HeaderType 且不包含该分类；此为所有者文档与实现待核对事项。本工程链接其契约，不复制旧描述或替其修改权威定义。

### 验证依据

最近有记录的完整 reactor 验证为 2026-10-09 的 `mvn -B verify`：单元验证完成，部分真实 Nacos/Redis/请求链场景通过，BrowserSessionIngressIT 两项因 user-auth `/api/challenges` 未消费当前 Client Header、channelCode 必填失败而未完成。无效 Cookie 401 已验证。**完整跨服务验收未通过，该记录也不是当前工作区重新验证结果。**

原始命令、报告数字、当时限制和历次迁移已保存在本地提交 `da804649f1efd0eeb3e20f3fca6a4268a3d0da75`，可读取：

```bash
git show da804649f1efd0eeb3e20f3fca6a4268a3d0da75:docs/RESPONSIBILITIES.md
```

该提交包含原始完整记录；本地未推送的提交不假定可由远端访问。报告位置、环境和执行入口见 [集成测试模块](../gateway-integration-tests/README.md)，场景说明不替代执行报告。后续只更新当前状态及必要证据，不在本文件追加改名、构造器或每轮测试流水账。
