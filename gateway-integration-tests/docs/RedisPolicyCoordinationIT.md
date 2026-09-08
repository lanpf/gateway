# RedisPolicyCoordinationIT

## 目的

在真实 Redis 上验证 `gateway-infrastructure-coordination-redis` 的策略协调能力：

- `PolicyPublishLock` 固定场景 `publish`、资源 `ingress-policy` 的发布锁在跨连接（多实例）维度互斥；
  未获得锁的发布方被拒绝且其业务动作不执行，持有方的业务结果正常生效。
- 发布动作抛出异常时锁被正常释放，后续发布可以继续。
- 发布方异常退出（未解锁）时，锁租约到期后其他发布方可以恢复发布。
- `RedisPolicyRollbackState` 是无 TTL 的持久状态：激活后对同一 Redis 的其它 adapter 实例可见、
  幂等重复激活不报错、清除后全局失效。
- rollbackState 按 namespace 隔离，不同命名空间的键互不影响。

## 条件

- Docker daemon 可用。
- 测试使用官方 `redis:8.4.4` 镜像（支持 arm64/amd64），随机映射 `6379` 端口。

Docker 不可用时，Testcontainers 会明确跳过本场景。

## 执行

```bash
mvn -pl gateway-integration-tests -am verify
```

## 手工观察

### IDE 断点

1. 使用 IDE 的 Debug Test 直接运行 `RedisPolicyCoordinationIT`。
2. 在 `manualObservationPoint()` 中读取 `MANUAL_OBSERVATION_SECONDS_PROPERTY` 的语句设置断点，
   断点挂起策略选择 `Suspend: All`。断点位于第一个测试方法的末尾。
3. 断点命中前，测试已经完成并发发布互斥、异常释放、锁键清理和再次发布；此时 `@AfterEach`
   尚未执行，Redis 容器仍可用（rollbackState 相关键已在本场景的断言流程中被清理）。
4. 完成观察后继续执行，测试进入 `@AfterEach` 并清理容器。

如果通过 Maven Failsafe 远程调试，可增加 `-Dmaven.failsafe.debug`，连接其调试端口后使用相同断点。

### 定时观察窗口

使用显式观察模式运行测试，例如保留 Redis 容器最多 10 分钟：

```bash
mvn -pl gateway-integration-tests -am \
  -Dit.test=RedisPolicyCoordinationIT \
  -Dgateway.it.manual-observation-seconds=600 \
  verify
```

测试会输出 Redis 地址与 `redis-cli` 命令以及一个 marker 文件路径，然后进入等待状态：

1. 使用输出的 `redis-cli -h <host> -p <port>` 连接 Redis。
2. 执行 `KEYS gateway-it:*` 观察锁键与 rollbackState 键的命名空间格式
   （例如 `gateway-it:publish:ingress-policy`、`gateway-it:policy:rollback-state`）。
3. 发布锁与 rollbackState 使用同一 `spring.application.name` 风格的 namespace 前缀；
   换一个 namespace 运行即可观察隔离效果。
4. 观察完成后删除输出的 marker 文件，测试会立即继续并清理容器；如果不删除，达到
   `gateway.it.manual-observation-seconds` 指定的时间后也会自动清理。

默认不设置该参数时不会等待，适用于 CI；IDE Debug 时可以只使用上述断点而不设置参数。

## 观察入口

- 手工观察模式输出的 Redis 地址、`redis-cli` 命令和 marker 文件
- `redis-cli` 的 `KEYS gateway-it*`、`TTL <key>` 输出
- `gateway-integration-tests/target/failsafe-reports/com.cloud.gateway.infrastructure.coordination.redis.RedisPolicyCoordinationIT.txt`
- Testcontainers/Redis 容器日志

## 预期结果

- 第二个发布方在锁被持有时立即收到 `APP_POLICY_PUBLISH_LOCK_UNAVAILABLE`，其业务动作未执行；
  持有方业务结果返回且锁键 `gateway-it:publish:ingress-policy` 在释放后消失。
- 发布动作抛出 `IllegalStateException` 后锁键消失，后续发布成功。
- 发布方放弃解锁后，约 1 秒短租约到期，竞争方重新获取成功。
- rollbackState 键 `gateway-it:policy:rollback-state` 激活后 TTL 为 `-1`（永不过期），
  跨 adapter 实例可见，激活幂等，`clear` 后对两个实例都失效，重复 `clear` 不报错。
- 命名空间 `gateway-it-other` 的 rollbackState 与 `gateway-it` 互不影响。
