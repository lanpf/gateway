# GatewayPipelineIT

网关管道集成冒烟场景。随机端口真实 SCG 与 Reactor Netty 下游。覆盖：身份/非认证凭据清除、无效 Browser Cookie 在匿名端点也返回 401 且不降级、完整快照切换后 Header 刷新、未知端点及缺失 App 提前拒绝、实际路由与端点 routeId 不一致拒绝、慢下游按当前快照超时返回 504。快照为内存源，Redis 精确性由 RateLimitIT 单独覆盖。

## 运行

```bash
mvn -pl gateway-integration-tests -am verify -Dit.test=GatewayPipelineIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
```

完整验证从根工程执行 `mvn verify`。本场景在内存中组装真实 SCG 并绑定随机 HTTP 端口，不需要 Docker；真实 Redis 场景由独立测试覆盖。

## 观察

本场景使用 `-Dgateway.it.manual-observation-seconds=30` 观察窗口（不提供 `manualObservationPoint()` 断点）。

Header 动态更新观察入口为 `-Dit.test=GatewayPipelineIT#shouldApplyHeaderSelectionFromNewSnapshotWithoutRestart -Dgateway.it.manual-observation-seconds=30`（最多 60 秒），按测试打印的 curl 地址请求；预期未选择的 App 输出被撤销、外部伪造 X-App-Platform 被清除、普通配置 X-GW-Version 为 v2，无需重启。可信字段不能映射为自定义名称。

未配置观察参数时不等待，恢复测试或窗口到期后自动清理。报告位于 gateway-integration-tests/target/failsafe-reports；容器日志用于定位启动失败。
