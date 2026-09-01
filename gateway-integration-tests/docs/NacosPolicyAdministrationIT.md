# NacosPolicyAdministrationIT

## 目的

验证 `GatewayPolicyCommandFacade` 通过五个彼此独立的 Nacos Definition dataId 保存策略，
并在两次显式发布时组装完整 `PolicySnapshot`。同时验证 Snapshot dataId 的当前版本和
Nacos History 中的上一业务版本均可读取。

## 条件

- Docker daemon 可用。
- 测试主机允许临时绑定四个位于同一端口族的本地端口，分别映射 Nacos Server API、
  Console、gRPC Client 和 gRPC Server 端口。

Docker 不可用时，Testcontainers 会明确跳过本场景。

## 执行

```bash
mvn -pl gateway-integration-tests -am verify
```

## 手工观察

### IDE 断点

1. 使用 IDE 的 Debug Test 直接运行 `NacosPolicyAdministrationIT`。
2. 在 `manualObservationPoint()` 中读取 `MANUAL_OBSERVATION_SECONDS_PROPERTY` 的语句设置断点，
   断点挂起策略选择 `Suspend: All`。
3. 断点命中前，测试已经完成两次发布、History 检查，并通过 HTTP 探针确认输出的
   `Nacos console` 地址可访问；此时 `@AfterEach` 尚未执行，Nacos 容器和六个 dataId 都保持可用。
4. 完成观察后继续执行，测试会进入 `@AfterEach` 并清理配置和容器。

如果通过 Maven Failsafe 远程调试，可增加 `-Dmaven.failsafe.debug`，连接其调试端口后使用
相同断点。

### 定时观察窗口

使用显式观察模式运行测试，例如保留 Nacos 容器最多 10 分钟：

```bash
mvn -pl gateway-integration-tests -am \
  -Dit.test=NacosPolicyAdministrationIT \
  -Dgateway.it.manual-observation-seconds=600 \
  verify
```

测试完成两次发布和自动断言后，会打印仍然有效的 `Nacos console` 地址以及一个 marker
文件路径，并在清理容器前进入等待状态。然后：

1. 打开输出的 Nacos 控制台地址。Nacos 3 的 Console 使用容器端口 `8080` 和根路径 `/`；
   测试输出的是该端口映射到宿主机后的临时地址，不是 Server API 的 `8848/nacos/`。
2. 在配置列表中选择 `GATEWAY` 分组，确认五个 Definition dataId
   和 `gateway-ingress-policy.yaml` 同时存在。
3. 查看 Snapshot dataId 的当前内容，确认 `snapshotVersion` 为 `2`；打开历史版本，
   确认版本 `1` 的内容不含 Route Access，而当前版本包含 `USER_AUTH.LOGIN`。
4. 观察完成后删除输出的 marker 文件，测试会立即继续并清理容器；如果不删除，达到
   `gateway.it.manual-observation-seconds` 指定的时间后也会自动清理。

默认不设置该参数时不会等待，适用于 CI；IDE Debug 时可以只使用上述断点而不设置参数。

## 观察入口

- 手工观察模式输出的临时 Nacos 控制台地址和 marker 文件
- Nacos 控制台的配置列表与历史版本页面
- `gateway-integration-tests/target/failsafe-reports/com.cloud.gateway.infrastructure.persistence.nacos.NacosPolicyAdministrationIT.txt`
- Testcontainers/Nacos 容器日志

## 预期结果

- Endpoint、Client App、Channel Access、Channel Credential、Route Access 五个 Definition
  dataId 均存在；未修改的 Channel Credential 保持为空定义。
- 保存 Client App 和 Channel Access 后，第一次发布生成业务版本 `1`，且没有 Route Access。
- 保存 Route Access 后，第二次发布生成业务版本 `2`，且 `contentHash` 与版本 `1` 不同。
- Snapshot dataId 保存版本 `2`，Nacos History 可读取业务版本 `1` 的完整快照。
