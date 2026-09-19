# gateway

基于 Spring Cloud Gateway 的入口安全与策略治理服务。v1 使用 App / Client / Subject、EndpointRegistry、CapabilityBundle / CapabilityBinding 与完整 IngressSnapshot。

能力交付与限制见 [职责及交付状态](docs/RESPONSIBILITIES.md)。

## 文档入口

- [领域设计 · v1](docs/DOMAIN.md)：统一语言、模型、不变量、策略合并、签名、安全边界与分层。
- [职责与协作 · v1](docs/RESPONSIBILITIES.md)：服务边界、下游协议、交付状态和验收条件。
- 本文件：模块、配置、构建、启动、验证。不另建技术方案或交接文档。

## 模块

| 模块 | 职责 |
| --- | --- |
| gateway-api | 管理 Facade、请求/响应与协议常量 |
| gateway-domain | 身份、端点、Bundle/Binding、Header、签名协议、配额聚合、缓存准入与快照规则 |
| gateway-application | 发布/回退/保存、分阶段入口、签名认证、配额及下游治理用例与端口 |
| gateway-infrastructure | 本地只读配置、规范化序列化、LKG、缓存与熔断适配 |
| gateway-infrastructure-persistence-nacos | 五份 Definition、完整 Snapshot、CAS、监听及历史 |
| gateway-infrastructure-coordination-redis | 发布/回退锁与持久回退状态 |
| gateway-infrastructure-security-spring | Token 解码／introspection、Browser Session、密钥与密码学适配 |
| gateway-infrastructure-replay-redis | 原子防重放 |
| gateway-infrastructure-ratelimit-redis | EXACT 令牌桶及有界本地故障降级 |
| gateway-interfaces | REST、Spring Security 入口、证据适配、Header 与 HTTP 治理生命周期 |
| gateway-boot | 运行时装配与可执行 Jar |
| gateway-integration-tests | 独立真实基础设施冒烟测试 |

## 构建与启动

要求 Java 17、Maven。不要使用系统默认的其他 JDK 代替验证。依赖 framework 及 user-auth-api 当前开发构件，需配置可访问的 Maven 仓库。

当前引用 framework-dependencies `0.4.0-SNAPSHOT`，需要包含 RequestHeader 枚举及 HeaderType 的 framework-core；本地联合开发先在相邻 framework 工程执行 `mvn install`。Header 字符串使用 `RequestHeader.APP_ID.headerName()`；共享 Header 消费与暂缓的下游迁移见 [协作契约](docs/RESPONSIBILITIES.md#协作契约)。

```bash
mvn clean test
mvn -pl gateway-boot -am package -DskipTests
java -jar gateway-boot/target/gateway-boot-0.3.0-SNAPSHOT.jar \
  --spring.config.additional-location=file:./config/application.yml
```

本地配置采用 Redis、Nacos 3.x 和 user-auth 服务发现。部署参数由部署侧配置覆盖；仓库配置不重复代码默认值，不包含环境变量占位符。不会自动升级或覆盖外部 Nacos/Redis 数据。

主要配置入口：

- `gateway.policy-definition`：本地只读 App、Client、端点、凭据、Bundle/Binding。
- `gateway.policy.nacos`：配置中心 dataId、group、历史与 LKG。
- `gateway.security.access-token`：JWT/JWK 或 Reference introspection，需与 user-auth 一致。
- `gateway.security.request-signature`：密钥引用解析及时间窗口。
- `spring.cloud.gateway.server.webflux.routes`：SCG 交付路由，id 与端点 routeId 同值。
- `gateway.headers`：`extra-headers`、`forbidden-set-headers`、`forbidden-remove-headers`。名称匹配忽略大小写；集合在启动时生成不可变 HeaderRules，变更需重启，不使用可变 Properties 逐请求读取。

本机管理路由使用保留 routeId `gateway-admin`，端点必须登记，不能作为代理路由绕过认证。启动验证普通端点引用的实际路由存在。

### 策略组合示例

以下为本地 `gateway.policy-definition.policies` 下的配置片段。先给受管理的公开只读端点添加 `public-read` 标签；不要把缓存 Bundle 绑定到登录或主体相关端点。Nacos 第五份 Definition 使用对应 camelCase 字段及 ISO-8601 Duration，由管理接口/编码器生成，不能直接复制本地绑定格式。

```yaml
bundles:
  ingress-protection:
    bundle-id: ingress-protection
    rate-limit:
      on-redis-failure: CLOSED
      rules:
        - { rule-id: ingress-ip, dimension: IP, capacity: 100, refill-per-second: 20, mode: EXACT }
  public-read:
    bundle-id: public-read
    timeout: { connect: 1s, read: 3s, total: 5s }
    circuit-breaker:
      failure-rate-threshold: 50
      slow-call-rate-threshold: 50
      slow-call-duration: 2s
      minimum-calls: 20
      sliding-window-size: 50
      half-open-calls: 5
      wait-duration: 10s
      fallback: STALE_CACHE
    cache: { ttl: 30s, stale-window: 10s, max-body-bytes: 65536, vary-headers: [accept-language] }
    headers:
      trusted-headers: [APP_ID, CLIENT_ID]
      credential-forwarding: { authorization-header: STRIP, cookies: STRIP_ALL }
bindings:
  - { binding-id: all-ingress, bundle-id: ingress-protection }
  - { binding-id: public-reads, bundle-id: public-read, tags: [public-read] }
```

Header 节点整体替换默认输出集合，示例没有选择的 Subject/Session 字段不会写入；需要时显式加入。普通 set/remove 的名称必须为 RequestHeader、extra-headers 或 X-Gateway- 前缀之一，再通过独立操作权限校验。标准协议字段不在 RequestHeader 中；仓库 config/application.yml 显式配置禁止设置/删除名单，不会因为加入 extra-headers 而绕过该名单。规则身份 `ingress-ip` 应保持稳定。快照发布前会校验 Bundle、Binding、端点和身份引用，配置错误不会静默降级。

有 MeterRegistry 时采集 `gateway.ingress.requests`、`gateway.ingress.ambiguous_endpoint_match` 与 `gateway.quota.decisions` 指标，固定标签不带调用方、端点或密钥；指标导出和告警由部署侧装配。歧义端点匹配是配置缺陷，只在该指标中对内可见，对外仍统一拒绝。不按每次限流输出 INFO 日志，Redis 故障进入/恢复时记录状态变化。阶段二（缓存、熔断、超时、降级）不属于 v1，无对应指标。

## Definition 与发布

Nacos group 默认为 GATEWAY，环境由 namespace 隔离。准备四份 Definition：

1. gateway-endpoints.yaml
2. gateway-app-policies.yaml
3. gateway-client-policies.yaml
4. gateway-policy-configuration.yaml

端点接入状态并入 `gateway-endpoints.yaml`：每个端点条目自带 `access` 节点，不再有独立的 endpoint-access Definition。凭据转发设置由 `gateway-policy-configuration.yaml` 的 HeaderPolicy 承载，端点与 Header 节点都不再声明 `credential-forwarding`。

完整快照 dataId 为 `gateway-policy.yaml`，本地 LKG 默认为 `data/gateway-policy.yaml`。示例位于 [配置示例目录](gateway-infrastructure-persistence-nacos/src/main/resources/examples)。

Admin 基础路径为 `/admin/gateway`；保存只修改 Definition，`POST /snapshot/publish` 显式发布，`POST /snapshot/rollback` 回退。策略组合通过 `PUT /policies/configuration` 保存。请求需通过 App 和管理 Bearer scope/audience 认证；具体载荷见 gateway-api。

策略组合 Java 类型统一使用 `CapabilityComposition`；本轮包名与快照返回类型调整不变更部署配置键、管理路径或 dataId。端点主体要求使用 `REQUIRED` / `ANONYMOUS`，旧 `OPTIONAL` 值需更新并重新生成快照摘要；Client 验证要求仍使用 `REQUIRED` / `OPTIONAL`。

Client 配置合并为 `clients`，凭据内嵌于 `credentials.activeCredential/graceCredential`；本地入口为 `gateway.policy-definition.clients`。接入设置和凭据更新共用 `gateway-client-policies.yaml` 的 revision，任一操作成功后另一操作须使用最新 revision。凭据操作要求 Client 已存在，接入设置不会覆盖其凭据。端点配置及管理请求以 `status: ENABLED` 或 `DISABLED` 替代 `enabled` 布尔值。旧分离 Definition、旧布尔字段及旧快照需重建，不提供混合读取兼容层。

端点与 Header 节点的凭据转发设置统一为 `credentialForwarding`（API/Nacos）或 `credential-forwarding`（本地 YAML），领域类型为 `CredentialForwarding`。不再使用 `credentialForwardingPolicy`、Header 下的 `credentials` 或端点下的 `forwarding`；Client 下的 `credentials` 不变。此更名会改变内容摘要，外部开发快照须重新生成，仓库示例已同步。

仅快照发布身份可写 Snapshot，运行身份只读。首次启动远端和 LKG 均无有效快照时失败。开发阶段破坏式更新后，应按新模型重新准备开发配置；旧 snapshot schema 不兼容。保留三种历史签名计算方式并不意味着保留旧配置结构。

无 Nacos 写入适配时，本地 Definition/Snapshot 只读，不装配 Admin 发布/回退命令。

## 验证

开发阶段：

```bash
mvn clean test
mvn -pl gateway-boot -am package -DskipTests
git diff --check
```

冒烟阶段需要 Docker；使用 Testcontainers 创建隔离资源，不使用外部业务 Redis/Nacos。运行方式：

```bash
mvn -pl gateway-integration-tests -am verify -Dit.test=RateLimitIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
# 完整验证从根工程执行，先由 reactor 构建 BrowserSessionIngressIT 所需的 gateway-boot Jar
mvn verify
```

Surefire 报告位于各模块 target/surefire-reports；Failsafe 报告位于 gateway-integration-tests/target/failsafe-reports。Docker 不可用造成的跳过不视为通过。

本轮验证结果、历史冒烟结果及暂缓的下游迁移状态统一见 [职责及交付状态](docs/RESPONSIBILITIES.md)；历史通过数不代表当前工作区已通过相同验收。

### RateLimitIT 观察

使用官方 `redis:8.4.4`（已验证 amd64/arm64 支持），复用既有 smoke 镜像，作为轻量镜像偏好的明确例外。覆盖两个独立 Spring 上下文共享额度、24 个竞争请求分享 7 个令牌、拒绝保留分数令牌、Redis TIME 补充、热更新先按旧速率结算且不重新满额、同维度全规则与最大 Retry-After、命名空间隔离、真实 WRONGTYPE 故障下 CLOSED 与有界本地降级。

手动观察可指定 `-Dit.test=RateLimitIT#shouldShareExactCapacityAcrossIndependentGatewayInstances -Dgateway.it.manual-observation-seconds=30`（此场景最大 60 秒）。测试输出隔离容器的 `redis-cli HGETALL` / `PTTL` 命令；预期 tokens=0、capacity=7、refill=1、TTL 为正。到期自动清理上下文、键与容器。以测试报告实际结果为准。

### 其他既有场景

可将上述命令的 `-Dit.test` 替换为以下测试类。单独运行 BrowserSessionIngressIT 前必须先执行 `mvn -pl gateway-boot -am package -DskipTests`，完整 smoke 则使用根目录 `mvn verify`，避免拿缺失或旧的启动 Jar 验证。Nacos 使用 3.0.3，需要允许临时绑定 API、Console、gRPC 端口族；测试创建隔离容器和配置，完成后自动清理。

| 场景 | 自动断言及观察目标 |
| --- | --- |
| NacosPolicyAdministrationIT | 四份 Definition 与 Snapshot 共五个 dataId；接入与凭据共用 revision，旧接入写入被拒绝，嵌套凭据随快照发布；两次发布产生版本 1/2，History 保留版本 1，hash 改变。 |
| AccessSnapshotPropagationIT | MD5 CAS、多实例监听收敛；旧请求引用完整保留版本 1，新读取观察版本 2，无混用。 |
| RedisPolicyCoordinationIT | 跨连接锁互斥、异常释放、租约恢复；rollbackState 跨实例可见、TTL=-1、幂等清除、namespace 隔离。锁资源 ingress-policy 保留，不随模型改名迁移。 |
| RedisReplayGuardIT | 两个连接工厂 10 个并发登记恰好一次成功；TTL 到期可重新登记，过期证据不写入，namespace 隔离。 |
| GatewayPipelineIT | 随机端口真实 SCG 与 Reactor Netty 下游；身份/凭据清除、完整快照切换后 Header 刷新、未知端点及缺失 App 提前拒绝、实际路由与端点 routeId 不一致拒绝。阶段二能力已不属于 v1，不再断言缓存命中与超时降级；快照为内存源，Redis 精确性由 RateLimitIT 单独覆盖。 |
| BrowserSessionIngressIT | 双应用真实 Nacos/MySQL/Redis；有效 Cookie 及允许 Origin 放行并结束会话，无效 Cookie 401、非法 Origin 403；需同级 user-auth/config/db/schema.sql 及兼容的开发构件，不能用单元测试代替协作验收。 |

Nacos、协调与快照场景可在 `manualObservationPoint()` 设置断点（`RedisPolicyCoordinationIT`、`AccessSnapshotPropagationIT`、`NacosPolicyAdministrationIT`、`RedisReplayGuardIT` 四个场景提供该方法）；`RateLimitIT`、`GatewayPipelineIT` 与 Browser 场景改用 `-Dgateway.it.manual-observation-seconds=30`，Browser 场景为其自身的观察断点。Nacos 测试输出实际控制台地址（映射容器 8080 的根路径，不是 8848/nacos）；Redis 测试输出连接命令及 namespace。只在输出的隔离测试资源中检查键和 TTL；若输出 marker，可删除该具体 marker 提前结束观察。未配置观察参数时不等待，恢复测试或窗口到期后自动清理。报告位于前述 Failsafe 目录，容器日志用于定位启动失败。

GatewayPipelineIT 的 Header 动态更新观察入口为 `-Dit.test=GatewayPipelineIT#shouldApplyHeaderSelectionFromNewSnapshotWithoutRestart -Dgateway.it.manual-observation-seconds=30`（最多 60 秒），按测试打印的 curl 地址请求；预期未选择的 App 输出被撤销、外部伪造 X-Gateway-App 被清除、普通配置 X-Gateway-Version 为 v2，无需重启。可信字段不能映射为自定义名称。

为遵循本项目三份文档上限，集成场景目的、运行命令与观察说明合并在本节，不再新增逐场景 Markdown。
