# RedisPolicyCoordinationIT

发布/回退协调集成冒烟场景。覆盖：跨连接锁互斥、异常释放、租约恢复；rollbackState 跨实例可见、TTL=-1、幂等清除、namespace 隔离。锁资源 ingress-policy 保留，不随模型改名迁移。

## 运行

```bash
mvn -pl gateway-integration-tests -am verify -Dit.test=RedisPolicyCoordinationIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
```

完整验证从根工程执行 `mvn verify`。测试使用 Testcontainers 创建隔离 Redis 资源，不使用外部业务 Redis/Nacos，完成后自动清理。Docker 不可用造成的跳过不视为通过。

## 观察

可在 `manualObservationPoint()` 设置断点。Redis 测试输出连接命令及 namespace。只在输出的隔离测试资源中检查键和 TTL；若输出 marker，可删除该具体 marker 提前结束观察。未配置观察参数时不等待，恢复测试或窗口到期后自动清理。

报告位于 gateway-integration-tests/target/failsafe-reports；容器日志用于定位启动失败。
