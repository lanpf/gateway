# RedisReplayGuardIT

原子防重放集成冒烟场景。覆盖：两个连接工厂 10 个并发登记恰好一次成功；TTL 到期可重新登记，过期证据不写入，namespace 隔离。

## 运行

```bash
mvn -pl gateway-integration-tests -am verify -Dit.test=RedisReplayGuardIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
```

完整验证从根工程执行 `mvn verify`。测试使用 Testcontainers 创建隔离 Redis 资源，不使用外部业务 Redis/Nacos，完成后自动清理。Docker 不可用造成的跳过不视为通过。

## 观察

可在 `manualObservationPoint()` 设置断点。Redis 测试输出连接命令及 namespace。只在输出的隔离测试资源中检查键和 TTL；若输出 marker，可删除该具体 marker 提前结束观察。未配置观察参数时不等待，恢复测试或窗口到期后自动清理。

报告位于 gateway-integration-tests/target/failsafe-reports；容器日志用于定位启动失败。
