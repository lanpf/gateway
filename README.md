# gateway

基于 Spring Cloud Gateway 的入口安全与策略治理服务。v1 使用 App / Client / Subject、EndpointRegistry、CapabilityBundle / CapabilityBinding 与完整 IngressSnapshot。

能力交付与限制见 [职责及交付状态](docs/RESPONSIBILITIES.md)。

## 文档入口

- [领域设计 · v1](docs/DOMAIN.md)：统一语言、身份与关系、不变量、决策顺序、用例、错误与事件。
- [职责与协作 · v1](docs/RESPONSIBILITIES.md)：服务边界、跨服务协作、对外保证与限制、当前交付和验证依据。
- [协议表示 · v1](docs/PROTOCOL.md)：签名报文、配置与快照表示、规范化编码。
- [专属技术设计](docs/DESIGN.md)：能力目录、校验复用、协议适配、执行保护及恢复观测。
- 本文件：工程概览、模块、配置入口、构建、启动和验证。任务涉及相应内容时读取其权威文档；必要的项目专属协议与设计由此路由。

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

当前引用 framework-dependencies `0.4.0-SNAPSHOT`，需要包含 RequestHeader 契约及 HeaderType 的 framework-core；本地联合开发先在相邻 framework 工程执行 `mvn install`。共享 Header 与下游消费协作见 [职责文档](docs/RESPONSIBILITIES.md#framework-与共享-header)。

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
- `gateway.header`：部署 `deny-set`、`deny-remove` 入口；参数与装配见 [技术设计](docs/DESIGN.md)，策略语义见 [领域规则](docs/DOMAIN.md#6-header-与信任上下文)。

本机管理路由的保留 routeId 为 `gateway-admin`，SCG 路由 id 与端点 routeId 对应。启动与入口适配见 [技术设计](docs/DESIGN.md#入口接入与协议适配)。

### 本地能力配置示例

以下片段置于 `gateway.access-definition.policies`，示例端点需要 `public-read` 受管理标签。报文/字段约定见 [配置表示](docs/PROTOCOL.md#配置表示)，组合和默认规则见 [DOMAIN](docs/DOMAIN.md#3-能力组合与绑定解析)。

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

## 配置中心与管理入口

Nacos group 默认 GATEWAY，环境通过 namespace 隔离。四份 Definition、完整 Snapshot 的文件名和 schema 见 [协议文档](docs/PROTOCOL.md#快照与规范化编码)，完整示例见 [配置示例目录](gateway-infrastructure-persistence-nacos/src/main/resources/examples)。完整快照 dataId 为 `gateway-access.yaml`，本地 LKG 默认 `data/gateway-access.yaml`。

部署使用 `gateway.access-definition`、`gateway.access.nacos` 当前前缀，执行保护和租约/超时关系见 [DESIGN](docs/DESIGN.md#发布与恢复执行保护)。当前配置结构及兼容限制以协议文档为准，不在运行入口累积改名或摘要迁移历史。

管理调用的端点、权限和只读边界见 [职责文档](docs/RESPONSIBILITIES.md#管理调用方)，能力组合载荷见 [协议示例](docs/PROTOCOL.md#管理能力组合载荷示例)。

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

验证结果需附命令、环境、范围与失败情况；历史通过不能证明当前工作区通过。场景说明见以下索引。

## 集成场景索引

场景覆盖、环境要求、统一执行、报告位置及逐场景文档见 [集成测试模块](gateway-integration-tests/README.md)。
