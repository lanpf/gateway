# CapabilityAssemblyIT

验证 interfaces 的真实装配调用 application 与 domain：共享 Validator 被执行，各运行能力使用各自类型的解析策略。

## 条件与运行

Java 17、Maven，依赖由 reactor 构建；本场景在内存中运行，无外部数据或容器。

```bash
mvn -pl gateway-integration-tests -am verify -Dit.test=CapabilityAssemblyIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
```

报告位于 gateway-integration-tests/target/failsafe-reports。

## 人工观察

在 shouldWireEveryCapabilityAndItsTypedConfigurationSource() 的 preAuth／output 断言处设置断点，调试 Failsafe。观察 acquired、executed 和两阶段 outcomes；在另一个场景观察 validations。 使用 IDE 的 Maven 调试模式启动上述 verify 命令，连接 Failsafe 调试端口（可添加 -Dmaven.failsafe.debug）。观察后恢复线程，Spring 上下文由 try-with-resources 关闭；HTTP 测试服务器由 WebTestClient 的内存绑定管理。

预期：前置阶段仅执行配置的配额；FORWARD 执行 timeout 与 header-apply，保持原解析对象，不重复获取配额；共享 Validator 被调用一次。
