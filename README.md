# gateway

统一外部入口，负责验证 OAuth2 Access Token、校验客户端与渠道上下文，并向下游服务写入可信请求头。

gateway 的领域边界是 **Ingress Access Control（入口访问控制）**。它负责把外部不可信请求转换为内部可信请求上下文，但不拥有用户账户认证、功能权限或业务资源授权。完整模型见 [领域设计](docs/DOMAIN.md)。

## 模块

| 模块 | 职责 |
| --- | --- |
| `gateway-api` | Admin Facade、Command、Output 和 REST 路径契约 |
| `gateway-domain` | EndpointCatalog、四类策略、快照、Subject、可信上下文和入口判定规则 |
| `gateway-application` | Definition 保存、显式发布、回退编排及安全端口 |
| `gateway-infrastructure` | 本地配置 Definition、只读快照、LKG 抽象、统一存储模型和 contentHash |
| `gateway-infrastructure-persistence-nacos` | Nacos Definition、完整快照、MD5 CAS、监听和 History adapter |
| `gateway-infrastructure-coordination-redis` | Publish/Rollback 分布式锁装配和持久 rollbackState adapter |
| `gateway-infrastructure-security-spring` | JWT/Reference Token 与 Channel Request Signature adapter |
| `gateway-infrastructure-replay-redis` | 跨实例原子防重放 adapter |
| `gateway-interfaces` | Admin REST、Facade 实现和 SCG GlobalFilter |
| `gateway-boot` | 最终运行时装配和可执行 Jar |
| `gateway-integration-tests` | 基于 Testcontainers 的真实中间件集成测试 |

## 信任边界

- 外部调用方提供 `X-Client-App-Id`、`X-Client-Version` 和可选的 `X-Channel-Code`。
- 网关根据 `gateway.ingress-policy.client-apps` 校验 ClientApp 状态与渠道合法性，并由 ClientApp 配置推导 `X-Client-Platform`。
- 网关验证 Bearer Access Token；以 `sub` 和 `session_id` 写入 `X-Subject-Type=HOST_SESSION`、`X-User-Id`、`X-Session-Id`。Browser Session 验证后写入同样的用户/会话 Header，但 Subject Type 为 `BROWSER_SESSION`。
- 所有受保护 Header 都会先删除再由网关写入，客户端提交的同名值不会透传。
- `IngressClientAppPolicy`、`IngressChannelAccessPolicy`、`IngressChannelCredentialPolicy`、`IngressRouteAccessPolicy` 是 gateway 自有的四类入口策略，统一校验并发布为一份 `IngressPolicySnapshot`。运行时使用 Nacos adapter；`PolicySnapshotPublisher/Provider/History` 端口隔离了发布技术，Nacos 不是领域模型的一部分。
- Browser Session 确定由 gateway 在线验证，并以受限 Subject Type 转发；当前只定义端口并默认 fail-closed，尚未接入真实 Session Store adapter。
- 内部服务调用暂时以可信内网为边界，可以直接调用并传播现有请求上下文，不额外设计服务身份或 delegation；该简化必须由私网、网络策略/安全组和禁止下游公网暴露共同保证。
- 下游业务服务必须只允许来自 gateway 或可信内网的流量；Header 本身不是跨越不可信网络的认证机制。

`gateway.security.access-token.format` 可选 `SELF_CONTAINED`（JWT + JWK）或 `REFERENCE`（OAuth2 Token Introspection）。它必须与 user-auth 的 Access Token 格式一致；该差异只属于 infrastructure 验证策略，不进入 `AuthenticatedSubject` 或下游可信请求上下文。

## 启动

```bash
mvn test
mvn package
java -jar gateway-boot/target/gateway-boot-0.2.0-SNAPSHOT.jar \
  --spring.config.additional-location=file:./config/application.yml
```

本地启动需要 Redis 和 Nacos 3.x。启动前必须在目标 namespace 的 `GATEWAY` group 中准备[五份 Definition 示例](gateway-infrastructure-persistence-nacos/src/main/resources/examples/definitions)，并发布一份有效的[完整 Snapshot 示例](gateway-infrastructure-persistence-nacos/src/main/resources/examples/gateway-ingress-policy.yaml)：`gateway-ingress-policy.yaml`。首次启动时 Nacos 和本机 last-known-good 都没有有效 Snapshot 会 fail-fast；成功加载后，最后有效原始 YAML 原子写入 `./data/gateway-ingress-policy.yaml`。

常用环境变量：

- `NACOS_SERVER_ADDR`、`NACOS_NAMESPACE`、`NACOS_USERNAME`、`NACOS_PASSWORD`：Nacos 客户端连接和认证。
- `NACOS_ADMIN_ACCESS_TOKEN`、`GATEWAY_POLICY_NACOS_HISTORY_CONNECT_TIMEOUT`、
  `GATEWAY_POLICY_NACOS_HISTORY_READ_TIMEOUT`：Nacos History REST 客户端的可选 token 和独立超时；
  未提供 token 时使用 username/password 登录。
- `GATEWAY_POLICY_NACOS_GROUP`、`GATEWAY_POLICY_NACOS_DATA_ID`、五个 `GATEWAY_POLICY_*_DEFINITION_DATA_ID`：Snapshot 和 Definition 资源。
- `GATEWAY_POLICY_LKG_LOCATION`：本机 last-known-good 路径。Redis 资源 namespace 默认使用 `spring.application.name`。

Admin 保存只修改对应 Definition dataId，并通过 Nacos MD5 做 CAS；`POST /admin/gateway/policy-snapshots/publish` 才组装、校验并发布完整 Snapshot。`POST /admin/gateway/policy-snapshots/rollback` 从 Nacos History 读取目标内容，恢复全部 Definition 后以更大的 `snapshotVersion` 发布。回退失败时 Redis `rollbackState` 保留，普通 Publish 被阻断，但可以用相同或不同目标版本再次回退。没有配置中心时，`gateway.ingress-policy` 由共享 infrastructure 作为默认只读 Definition 数据源，只装配运行时读取能力，不装配 Publisher、Admin 命令、Facade 和 REST 入口。

生产环境应通过环境变量替换 introspection client secret，并按 Endpoint 的 ChannelCredentialRequirement 完成 ClientApp、Channel Access 校验或 Channel Request Signature 验证。

## 文档入口

- [领域设计](docs/DOMAIN.md)：入口访问控制的统一语言、领域模型、策略、应用端口、分层建议和当前实现迁移方向。
