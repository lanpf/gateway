# PolicySnapshotPropagationIT

## 目的

验证真实 Nacos 3.0.3 中完整入口策略快照的 MD5 CAS 发布，以及两个独立
`NacosPolicySnapshotStore` 实例通过配置监听最终切换到同一业务版本。

## 条件

- Docker daemon 可用。
- 测试主机允许临时绑定四个位于同一端口族的本地端口，分别映射 Nacos Server API、
  Console、gRPC Client 和 gRPC Server 端口。

Docker 不可用时 Testcontainers 明确跳过本场景。

## 执行

```bash
mvn -pl gateway-integration-tests -am verify
```

## 手工观察

### IDE 断点

1. 使用 IDE 的 Debug Test 直接运行 `PolicySnapshotPropagationIT`。
2. 在 `manualObservationPoint()` 中读取 `MANUAL_OBSERVATION_SECONDS_PROPERTY` 的语句设置断点，
   断点挂起策略选择 `Suspend: All`。
3. 断点命中前，两个 Store 已经切换到版本 `2`，测试也已通过 HTTP 探针确认输出的
   `Nacos console` 地址可访问；此时 `@AfterEach` 尚未执行。
4. 在控制台查看 `GATEWAY` 分组下的 `gateway-ingress-policy.yaml` 和 History，完成后
   继续执行，让 `@AfterEach` 清理资源。

通过 Maven Failsafe 远程调试时可增加 `-Dmaven.failsafe.debug`，连接调试端口后使用相同断点。

### 定时观察窗口

不使用调试器时，可以保留容器最多 10 分钟：

```bash
mvn -pl gateway-integration-tests -am \
  -Dit.test=PolicySnapshotPropagationIT \
  -Dgateway.it.manual-observation-seconds=600 \
  verify
```

测试会输出控制台地址和 marker 文件。Nacos 3 的 Console 使用容器端口 `8080` 和根路径 `/`，
测试输出的是映射到宿主机后的临时地址；删除 marker 可提前结束等待，否则超时后自动清理。

## 观察入口

- IDE 断点或定时观察窗口输出的 Nacos 控制台地址
- `gateway-integration-tests/target/failsafe-reports/com.cloud.gateway.infrastructure.persistence.nacos.PolicySnapshotPropagationIT.txt`
- Testcontainers/Nacos 容器日志

## 预期结果

- 初始版本 `1` 被发布到 Nacos。
- 第一个 Store 以当前 MD5 成功 CAS 发布版本 `2`。
- 第二个 Store 经完整内容监听、校验并原子切换到版本 `2`。
- 两个 Store 最终具有相同的 `contentHash`。
