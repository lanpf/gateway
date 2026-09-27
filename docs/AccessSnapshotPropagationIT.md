# AccessSnapshotPropagationIT

快照传播集成冒烟场景。覆盖：MD5 CAS、多实例监听收敛；旧请求引用完整保留版本 1，新读取观察版本 2，无混用。

## 运行

```bash
mvn -pl gateway-integration-tests -am verify -Dit.test=AccessSnapshotPropagationIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
```

完整验证从根工程执行 `mvn verify`。Nacos 使用 3.0.3，需要允许临时绑定 API、Console、gRPC 端口族；测试创建隔离容器和配置，完成后自动清理。Docker 不可用造成的跳过不视为通过。

## 观察

可在 `manualObservationPoint()` 设置断点。Nacos 测试输出实际控制台地址（映射容器 8080 的根路径，不是 8848/nacos）。只在输出的隔离测试资源中检查键和 TTL；若输出 marker，可删除该具体 marker 提前结束观察。未配置观察参数时不等待，恢复测试或窗口到期后自动清理。

报告位于 gateway-integration-tests/target/failsafe-reports；容器日志用于定位启动失败。
