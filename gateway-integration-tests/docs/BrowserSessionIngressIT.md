# BrowserSessionIngressIT

浏览器会话入口协作验收场景。双应用真实 Nacos/MySQL/Redis。覆盖：有效 Cookie 及允许 Origin 放行并结束会话，无效 Cookie 401、有效 Cookie 的非法 Origin 403（先完成真实会话签发；无效 Cookie 不用于替代 Origin 校验）。

需同级 user-auth/config/db/schema.sql 及兼容的可执行开发构件 user-auth-boot/target/user-auth-boot-0.4.3-SNAPSHOT-exec.jar（该工程使用 exec classifier），不能用单元测试代替协作验收。

## 运行

单独运行本场景前必须先执行 `mvn -pl gateway-boot -am package -DskipTests`（完整 smoke 则使用根目录 `mvn verify`），避免拿缺失或旧的启动 Jar 验证：

```bash
mvn -pl gateway-boot -am package -DskipTests
mvn -pl gateway-integration-tests -am verify -Dit.test=BrowserSessionIngressIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
```

冒烟阶段需要 Docker；测试创建隔离资源，完成后自动清理。Docker 不可用造成的跳过不视为通过。

## 观察

本场景使用 `-Dgateway.it.manual-observation-seconds=30` 观察窗口，其观察断点为场景自身的观察入口。未配置观察参数时不等待，恢复测试或窗口到期后自动清理。

报告位于 gateway-integration-tests/target/failsafe-reports；容器日志用于定位启动失败。
