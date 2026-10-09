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
| gateway-domain | 身份、端点、Bundle/Binding、Header、签名协议、配额聚合与快照规则 |
| gateway-application | 发布/回退/保存、分阶段入口、签名认证、配额及下游治理用例与端口 |
| gateway-infrastructure | 本地只读配置、规范化序列化、LKG 适配 |
| gateway-infrastructure-persistence-nacos | 四份 Definition、完整 Snapshot、CAS、监听及历史 |
| gateway-infrastructure-coordination-redis | 发布/回退锁与持久回退状态 |
| gateway-infrastructure-security-spring | Token 解码／introspection、Browser Session、密钥与密码学适配 |
| gateway-infrastructure-replay-redis | 原子防重放 |
| gateway-infrastructure-ratelimit-redis | EXACT 令牌桶及有界本地故障降级 |
| gateway-interfaces | REST、Spring Security 入口、证据适配、Header 与 HTTP 治理生命周期 |
| gateway-boot | 运行时装配与可执行 Jar |
| gateway-integration-tests | 独立跨模块与真实基础设施冒烟测试 |

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

- `gateway.access-definition`：本地只读 App、Client、端点、凭据、Bundle/Binding。
- `gateway.access.nacos`：配置中心 dataId、group、历史与 LKG。
- `gateway.security.access-token`：JWT/JWK 或 Reference introspection，需与 user-auth 一致。
- `gateway.security.request-signature`：密钥引用解析及时间窗口。
- `spring.cloud.gateway.server.webflux.routes`：SCG 交付路由，id 与端点 routeId 同值。
- `gateway.header`：`deny-set`、`deny-remove`，按操作独立否决普通 set／remove。名称匹配忽略大小写并规范化小写；集合在启动时生成不可变 HeaderConfigRule，变更需重启，不使用可变 Properties 逐请求读取。

本机管理路由使用保留 routeId `gateway-admin`，端点必须登记，不能作为代理路由绕过认证。启动验证普通端点引用的实际路由存在。

### 能力组合示例

能力配置类型统一使用 `*Config`，解析结果使用 `Effective*Policy`；bundles 与 bindings 均为按 ID 索引的对象，不再接收 bindings 数组；本次更新改变 HTTP／Nacos 结构与快照摘要格式。对应关系及默认值见 [领域设计](docs/DOMAIN.md#3-能力组合与绑定解析)。

以下为本地 `gateway.access-definition.policies` 下的配置片段。先给受管理的公开只读端点添加 `public-read` 标签。Bundle 内统一使用 capabilities 容器；能力标识为精确的注册名称，能力内部字段使用 camelCase，Duration 使用 ISO-8601。Bundle／Binding 外层的本地绑定字段仍使用 kebab-case，Nacos 文档使用 camelCase。

当前注册的能力节点为 `rate-limit`、`timeout`、`header-apply`。新增能力需部署策略组合器、必要的独立组合约束、运行实现和受控编解码注册；未支持的节点会被拒绝。交付边界见 [职责及交付状态](docs/RESPONSIBILITIES.md)。

能力组合的保存、发布、编解码与请求组装共用 CapabilityPolicyConfigValidator；装配处创建共享 Validator 并注入 Assembler，保存、发布和编解码直接调用 Validator，Assembler 在组合前调用内容校验；CapabilityConfigRule 仅接收各能力的完整配置列表。职责见 [领域设计](docs/DOMAIN.md#3-能力组合与绑定解析)。

```yaml
bundles:
  ingress-protection:
    bundle-id: ingress-protection
    capabilities:
      rate-limit:
        onRedisFailure: CLOSED
        rules:
          - { ruleId: ingress-ip, dimension: IP, capacity: 100, refillPerSecond: 20, mode: EXACT }
  public-read:
    bundle-id: public-read
    capabilities:
      timeout: { connect: PT1S, read: PT3S, total: PT5S }
      header-apply:
        trustedHeaders: [APP_ID, CLIENT_ID]
        credentialForwarding: { authorizationHeader: STRIP, cookies: STRIP_ALL }
bindings:
  all-ingress: { binding-id: all-ingress, bundle-id: ingress-protection }
  public-reads: { binding-id: public-reads, bundle-id: public-read, tags: [public-read] }
```

Header 节点整体替换默认输出集合，示例没有选择的 Subject/Session 字段不会写入；需要时显式加入。未配置 header-apply 时按默认计划执行：全部受支持可信字段 + 全剥离凭据（见 [领域设计](docs/DOMAIN.md#6-header-与信任上下文)）。普通 set/remove 的名称不得是已登记的 RequestHeader，其余合法 token 名称均可使用，再由部署 deny 名单独立否决。标准协议字段不在 RequestHeader 中；仓库 config/application.yml 显式配置禁止设置/删除名单。规则身份 `ingress-ip` 应保持稳定。快照发布前会校验 Bundle、Binding、端点和身份引用，配置错误不会静默降级。

有 MeterRegistry 时采集 `gateway.ingress.requests`、`gateway.ingress.ambiguous_endpoint_match` 与 `gateway.quota.decisions` 指标，固定标签不带调用方、端点或密钥；指标导出和告警由部署侧装配。歧义端点匹配是配置缺陷，只在该指标中对内可见，对外仍统一拒绝。不按每次限流输出 INFO 日志，Redis 故障进入/恢复时记录状态变化。

## Definition 与发布

Nacos group 默认为 GATEWAY，环境由 namespace 隔离。准备四份 Definition：

1. gateway-endpoints.yaml
2. gateway-app-capabilities.yaml
3. gateway-client-capabilities.yaml
4. gateway-access-configuration.yaml

端点接入状态并入 `gateway-endpoints.yaml`：每个端点条目自带 `access` 节点，不再有独立的 endpoint-access Definition。凭据转发设置仅由 `gateway-access-configuration.yaml` 中的 `capabilities.header-apply.credentialForwarding` 承载，端点不再单独声明。

完整快照 dataId 为 `gateway-access.yaml`，本地 LKG 默认为 `data/gateway-access.yaml`。示例位于 [配置示例目录](gateway-infrastructure-persistence-nacos/src/main/resources/examples)。

Admin 基础路径为 `/admin/gateway`；`PUT /policies/configuration` 整体保存能力组合 Definition，返回新的 `sourceRevision`，不会自动发布。请求需通过 App 和管理 Bearer scope/audience 认证；具体载荷见 gateway-api。发布／回退是独立用例，管理 HTTP 入口的已验证范围与已知限制见 [交付状态](docs/RESPONSIBILITIES.md#交付状态)。

能力组合保存请求示例（`expectedSourceRevision` 必须替换为当前 `gateway-access-configuration.yaml` 的 revision；初始化 Definition 仍通过配置中心完成）：

```json
{
  "expectedSourceRevision": "current-definition-revision",
  "policies": {
    "bundles": {
      "default": {
        "bundleId": "default",
        "capabilities": {
          "timeout": { "connect": "PT1S", "read": "PT3S", "total": "PT5S" }
        }
      }
    },
    "bindings": {
      "all": { "bindingId": "all", "bundleId": "default", "tags": [] }
    }
  }
}
```

两份索引的 key 必须与条目内 ID 一致，重复 key 及规范化后的重复 ID 均拒绝。旧 bindings 数组不兼容，开发 Definition、快照和 LKG 需按新结构重新生成；不提供旧结构兼容层。

这是整份替换，不是局部更新；未携带的 Bundle／Binding 会从编辑态移除。能力标识和内部字段与本地／Nacos 示例一致，HTTP 数组必须使用 JSON 数组，不能使用本地 Properties 的数字索引对象。管理身份从已认证上下文注入，不应在 body 中声明 userId／sessionId 来证明权限。

能力组合 Java 类型统一使用 `CapabilityPolicyConfig`。本轮同时更名部署配置键与 dataId：配置前缀 `gateway.policy-definition` / `gateway.policy.nacos` 改为 `gateway.access-definition` / `gateway.access.nacos`；dataId 改为 `gateway-access.yaml`（完整快照）、`gateway-app-capabilities.yaml`、`gateway-client-capabilities.yaml`、`gateway-access-configuration.yaml`，`gateway-endpoints.yaml` 不变。管理路径不变。端点主体要求使用 `REQUIRED` / `ANONYMOUS`，旧 `OPTIONAL` 值需更新并重新生成快照摘要；Client 验证要求仍使用 `REQUIRED` / `OPTIONAL`。

Client 配置合并为 `clients`，凭据内嵌于 `credentials.activeCredential/graceCredential`；本地入口为 `gateway.access-definition.clients`。接入设置和凭据更新共用 `gateway-client-capabilities.yaml` 的 revision，任一操作成功后另一操作须使用最新 revision。凭据操作要求 Client 已存在，接入设置不会覆盖其凭据。端点配置及管理请求以 `status: ENABLED` 或 `DISABLED` 替代 `enabled` 布尔值。旧分离 Definition、旧布尔字段及旧快照需重建，不提供混合读取兼容层。

本次能力配置为破坏式 schema 更新：Bundle 固定的 rateLimit／timeout／headers 字段改为 capabilities；旧本地配置、Definition 与快照需重新生成，不提供旧结构别名。能力内部字段采用 camelCase，包含 credentialForwarding。规范化摘要也随新结构改变；先部署支持全部配置能力的实例，再发布新配置，旧版本不能接纳未支持的能力。

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

## 集成场景索引

场景覆盖、环境要求、统一执行、报告位置及逐场景文档见 [集成测试模块](gateway-integration-tests/README.md)。
