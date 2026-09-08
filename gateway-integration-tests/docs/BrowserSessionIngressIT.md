# BrowserSessionIngressIT

## 测试目标

验证职责切分后 gateway 与 user-auth 的跨服务 Browser Session 协作契约。两个真实
Spring Boot 应用（gateway 反应式、user-auth Servlet）在同一测试 JVM 内启动，通过
真实 Nacos（入口策略快照）、MySQL（user-auth 业务数据）与 Redis（user-auth 会话存储）
基础设施完成端到端调用。

覆盖的具体契约：

1. gateway 从 Nacos 加载含 `USER_AUTH.BROWSER_SESSION_API` 入口类别的完整
   `IngressPolicySnapshot`，以 Spring Cloud LoadBalancer 的静态实例列表发现
   user-auth。
2. 携带有效 `BROWSER_SESSION` Cookie、命中 `allowedBrowserOrigins` 的
   `POST /api/user-auth/browser-sessions/end` 经 gateway 逐请求在线验证后转发，
   user-auth 以受限 `BROWSER_SESSION` authority 精确结束会话，并返回
   `Max-Age=0` 的清除 Cookie。
3. 结束后的同一凭据经 user-auth 内网验证端点返回 `verified=false`。
4. 无效 Cookie 被 gateway 判定为验证失败并以 `401` 拒绝，不转发下游。
5. 非 `allowedBrowserOrigins` 白名单内的非安全请求被入口策略以 `403` 拒绝，
   不触发在线验证。

## 运行条件

- JDK 17 或更高版本；本机 Docker 可用。
- Testcontainers 自动启动 Nacos 3.0.3（固定端口族）、MySQL 8.4.10 与 Redis 8.4.4，
  测试结束后自动清理。
- user-auth 以完整 `UserAuthApplication` 启动：真实 JPA、真实 Redis 会话存储、
  真实 SAS Token Endpoint；SQL 初始化脚本复用 user-auth 仓库的
  `config/db/schema.sql`（按 cloud 工作区同级目录约定定位 `../../user-auth/config/`），
  因此本场景只能在标准 cloud 工作区布局中运行。
- gateway 以完整 `GatewayApplication` 启动：Nacos adapter 加载测试内 seed 的
  EndpointCatalog、`IngressClientAppPolicy`（含 `allowedBrowserOrigins`）、
  Channel Access 与 Route Policy；`BrowserSessionVerifierAdapter` 通过
  LoadBalancer 调用真实 user-auth 验证端点。
- 测试前置动作（登录、handoff、兑换）直连 user-auth 完成，模拟 gateway 之外的
  既有宿主流程；`USER_AUTH.BROWSER_SESSION_API` 链路是唯一经 gateway 的路径。

## 执行方式

在 `gateway` 根目录执行：

```bash
mvn -pl gateway-integration-tests -am verify -Dit.test=BrowserSessionIngressIT
```

必须保留 `-am`，确保 gateway 与被依赖的 user-auth 构件使用当前工作区的最新产物；
user-auth 侧改动后需先 `mvn install -DskipTests -pl user-auth-boot -am`。

## 手工观测步骤

### IDE 断点检查双应用

1. 使用 IDE 以 Debug 模式运行 `BrowserSessionIngressIT`。
2. 在 `manualObservationBreakpoint()` 设置断点。到达断点时 gateway 与 user-auth
   均已启动，Browser Session Cookie 已在测试变量 `browserSession` 中。
3. 展开日志或调试面板可以直接看到：
   - MySQL/Redis/Nacos 容器 ID。
   - user-auth 与 gateway 的实际端口。
4. 通过 `http://127.0.0.1:<gatewayPort>/actuator/health` 确认 gateway 健康；
   通过 Nacos 控制台（`http://127.0.0.1:<consolePort>`，容器映射
   `HOST_HTTP_PORT+1`）检查 `GATEWAY` group 中的五份 Definition 与完整 Snapshot。
5. 使用 curl 重放结束请求观察 gateway 的 401/403 拒绝与 200 放行：
   ```bash
   curl -i -X POST http://127.0.0.1:<gatewayPort>/api/user-auth/browser-sessions/end \
     -H 'X-Client-App-Id: mini-program' -H 'X-Channel-Code: DIRECT' \
     -H 'Origin: https://h5.example.com' \
     -H 'Cookie: BROWSER_SESSION=<browserSession>'
   ```
6. 检查完成后恢复程序，测试继续断言并自动清理容器。

## 预期结果

- 正常结束：HTTP 200，响应含 `Set-Cookie: BROWSER_SESSION=; Max-Age=0`，
  随后内网验证返回 `verified=false`。
- 无效 Cookie：HTTP 401，user-auth 的结束端点未被调用。
- Origin 不在白名单：HTTP 403，user-auth 的结束端点未被调用。
