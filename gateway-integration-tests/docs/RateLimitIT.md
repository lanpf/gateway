# RateLimitIT

限流集成冒烟场景。使用官方 `redis:8.4.4` 镜像（已验证 amd64/arm64 支持），复用既有 smoke 镜像，作为轻量镜像偏好的明确例外。

覆盖：两个独立 Spring 上下文共享额度、24 个竞争请求分享 7 个令牌、拒绝保留分数令牌、Redis TIME 补充、热更新先按旧速率结算且不重新满额、同维度全规则与最大 Retry-After、命名空间隔离、真实 WRONGTYPE 故障下 CLOSED 与有界本地降级。

## 运行

```bash
mvn -pl gateway-integration-tests -am verify -Dit.test=RateLimitIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
```

完整验证从根工程执行 `mvn verify`。冒烟阶段需要 Docker；使用 Testcontainers 创建隔离资源，不使用外部业务 Redis/Nacos。Docker 不可用造成的跳过不视为通过。

## 观察

手动观察可指定 `-Dit.test=RateLimitIT#shouldShareExactCapacityAcrossIndependentGatewayInstances -Dgateway.it.manual-observation-seconds=30`（此场景最大 60 秒）。测试输出隔离容器的 `redis-cli HGETALL` / `PTTL` 命令；预期 tokens=0、capacity=7、refill=1、TTL 为正。到期自动清理上下文、键与容器。以测试报告实际结果为准。

Redis 测试输出连接命令及 namespace。只在输出的隔离测试资源中检查键和 TTL；若输出 marker，可删除该具体 marker 提前结束观察。未配置观察参数时不等待，恢复测试或窗口到期后自动清理。

报告位于 gateway-integration-tests/target/failsafe-reports；容器日志用于定位启动失败。
