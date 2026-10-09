# gateway-integration-tests

覆盖真实 Redis 锁、防重放及限流，Nacos Definition／Snapshot 管理与传播，Gateway 请求管线、Browser Session 跨服务协作，以及能力管理 HTTP／Facade 和运行装配的跨模块协作。

## 环境与执行

使用 Java 17、Maven；真实基础设施场景需要可用 Docker，由 Testcontainers 创建隔离资源并清理。Nacos 为 3.0.3，需允许临时 API、Console 和 gRPC 端口族。Browser 场景还需要相邻 user-auth 的当前可执行包，详细条件见场景文档。

在项目根目录执行：

```bash
mvn clean test
mvn verify
# 定向场景（Browser 场景先由根 reactor 构建 gateway-boot）
mvn -pl gateway-integration-tests -am verify -Dit.test=RateLimitIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
```

完整验证由根 reactor 先构建 gateway-boot Jar。不要用 Surefire 的 -Dtest 运行 *IT。两个能力协作场景在内存中执行，不需要容器；其余场景用隔离容器，不连接业务 Redis／Nacos。Docker 不可用时的跳过不视为通过。当前交付和已知限制见 [职责文档](../docs/RESPONSIBILITIES.md)。

## 报告与场景

单元报告为各模块 target/surefire-reports；集成报告为本模块 target/failsafe-reports。

| 场景 | 详细说明 |
| --- | --- |
| RateLimitIT | [RateLimitIT](docs/RateLimitIT.md) |
| NacosPolicyAdministrationIT | [NacosPolicyAdministrationIT](docs/NacosPolicyAdministrationIT.md) |
| AccessSnapshotPropagationIT | [AccessSnapshotPropagationIT](docs/AccessSnapshotPropagationIT.md) |
| RedisPolicyCoordinationIT | [RedisPolicyCoordinationIT](docs/RedisPolicyCoordinationIT.md) |
| RedisReplayGuardIT | [RedisReplayGuardIT](docs/RedisReplayGuardIT.md) |
| GatewayPipelineIT | [GatewayPipelineIT](docs/GatewayPipelineIT.md) |
| BrowserSessionIngressIT | [BrowserSessionIngressIT](docs/BrowserSessionIngressIT.md) |
| CapabilityPolicyConfigManagementIT | [CapabilityPolicyConfigManagementIT](docs/CapabilityPolicyConfigManagementIT.md) |
| CapabilityAssemblyIT | [CapabilityAssemblyIT](docs/CapabilityAssemblyIT.md) |
