# NacosPolicyAdministrationIT

Nacos 管理面集成冒烟场景。覆盖：四份 Definition 与 Snapshot 共五个 dataId；接入与凭据共用 revision，旧接入写入被拒绝，嵌套凭据随快照发布；两次发布产生版本 1/2，History 保留版本 1，hash 改变。

新增恢复路径：真实 Nacos Definition CAS 成功后注入预算耗尽，确认持久恢复流程保留阻断且不发布；读侧收敛后重试复用该检查点，发布版本 4，重复触发不再发布。该场景的锁与阻断端口在内存中；真实 Redis 排他、释放、租约与持久阻断另由 RedisPolicyCoordinationIT 验证。

## 运行

```bash
mvn -pl gateway-integration-tests -am verify -Dit.test=NacosPolicyAdministrationIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
```

完整验证从根工程执行 `mvn verify`。Nacos 使用 3.0.3，需要允许临时绑定 API、Console、gRPC 端口族；测试创建隔离容器和配置，完成后自动清理。Docker 不可用造成的跳过不视为通过。

## 观察

可在 `manualObservationPoint()` 设置断点。恢复过程可在 `verifyBudgetedRecovery()` 的超时断言和最终断言设置断点，观察 state、writes、版本 3/4 及控制台中的端点 Definition；预期超时后 state=true、writes=1，重试完成后 state=false，重复触发版本仍为 4。Nacos 测试输出实际控制台地址（映射容器 8080 的根路径，不是 8848/nacos）。只在输出的隔离测试资源中检查键和 TTL；若输出 marker，可删除该具体 marker 提前结束观察。未配置观察参数时不等待，恢复测试或窗口到期后自动清理。

报告位于 gateway-integration-tests/target/failsafe-reports；容器日志用于定位启动失败。
