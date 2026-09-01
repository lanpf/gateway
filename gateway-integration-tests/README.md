# gateway integration tests

覆盖 Nacos 独立策略定义、显式快照发布、历史版本、真实 CAS、监听通知和多实例传播。

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
