# CapabilityPolicyConfigManagementIT

验证真实 Controller、Facade、应用服务、领域与编解码器协作：整体保存、revision 冲突、阻断、校验代理及非法输入拒绝。

## 条件与运行

Java 17、Maven，依赖由 reactor 构建；本场景在内存中运行，无外部数据或容器。

```bash
mvn -pl gateway-integration-tests -am verify -Dit.test=CapabilityPolicyConfigManagementIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
```

报告位于 gateway-integration-tests/target/failsafe-reports。

## 人工观察

在 shouldSaveThroughHttpControllerAndReturnNextRevision() 的响应断言处和 Store.replace() 设置断点，调试 Failsafe。观察请求中的 capabilities、保存后的领域配置、writes 与 revision；在拒绝场景的最终断言处观察 writes=0。 使用 IDE 的 Maven 调试模式启动上述 verify 命令，连接 Failsafe 调试端口（可添加 -Dmaven.failsafe.debug）。观察后恢复线程，Spring 上下文由 try-with-resources 关闭；HTTP 测试服务器由 WebTestClient 的内存绑定管理。

预期：正常保存 revision 从 r1 推进到 r2，写入一次；非法输入、冲突及回退阻断均不写入，保存不发布快照。
