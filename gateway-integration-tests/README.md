# gateway integration tests

覆盖 Nacos 独立策略定义、显式快照发布、历史版本、真实 CAS、监听通知、多实例传播、传播窗口内的单快照版本语义，真实 Redis 上的策略协调锁、rollbackState 与跨实例原子防重放，以及 gateway 与 user-auth 双应用的跨服务 Browser Session 入口契约。

## 环境要求

- Java 17
- Maven
- 可用的 Docker daemon

## 执行

```bash
mvn -pl gateway-integration-tests -am verify
```

Failsafe 报告位于 `gateway-integration-tests/target/failsafe-reports/`。

## 场景文档

- [NacosPolicyAdministrationIT](docs/NacosPolicyAdministrationIT.md)
- [PolicySnapshotPropagationIT](docs/PolicySnapshotPropagationIT.md)
- [RedisPolicyCoordinationIT](docs/RedisPolicyCoordinationIT.md)
- [RedisReplayGuardIT](docs/RedisReplayGuardIT.md)
- [BrowserSessionIngressIT](docs/BrowserSessionIngressIT.md)
