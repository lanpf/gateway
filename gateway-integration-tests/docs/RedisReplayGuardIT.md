# RedisReplayGuardIT

## 目的

在真实 Redis 上验证 `gateway-infrastructure-replay-redis` 的跨实例原子防重放能力：

- `RedisReplayGuard.register` 使用 Redis `SET NX + TTL` 原子登记 timestamp/nonce 证据：
  多个 gateway 实例并发登记同一 evidence key 时，恰好一个实例成功，其余失败。
- 证据 TTL 到期后键消失，允许重新登记。
- `expiresAt` 已过期或等于当前时刻时，立即返回失败且不触碰 Redis。
- evidence key 按 namespace 隔离：不同 namespace 的同一 evidence key 互不影响。

## 条件

- Docker daemon 可用。
- 测试使用官方 `redis:8.4.4` 镜像（支持 arm64/amd64），随机映射 `6379` 端口。
- 场景内两个 guard 实例分别持有独立的 Lettuce 连接工厂，模拟两个 gateway 实例。

Docker 不可用时，Testcontainers 会明确跳过本场景。

## 执行

```bash
mvn -pl gateway-integration-tests -am verify
```

## 手工观察

### IDE 断点

1. 使用 IDE 的 Debug Test 直接运行 `RedisReplayGuardIT`。
2. 在 `manualObservationPoint()` 中读取 `MANUAL_OBSERVATION_SECONDS_PROPERTY` 的语句设置断点，
   断点挂起策略选择 `Suspend: All`。断点位于并发原子登记测试的末尾。
3. 断点命中前，10 个并发登记已完成且恰好一个成功；此时 `@AfterEach` 尚未执行，
   Redis 容器和 `gateway-it:replay:*` 证据键保持可用。
4. 完成观察后继续执行，测试进入 `@AfterEach` 并清理容器。

如果通过 Maven Failsafe 远程调试，可增加 `-Dmaven.failsafe.debug`，连接其调试端口后使用相同断点。

### 定时观察窗口

使用显式观察模式运行测试，例如保留 Redis 容器最多 10 分钟：

```bash
mvn -pl gateway-integration-tests -am \
  -Dit.test=RedisReplayGuardIT \
  -Dgateway.it.manual-observation-seconds=600 \
  verify
```

测试会输出 Redis 地址与 `redis-cli` 命令以及一个 marker 文件路径，然后进入等待状态：

1. 使用输出的 `redis-cli -h <host> -p <port>` 连接 Redis。
2. 执行 `KEYS gateway-it*` 观察证据键的命名空间格式
   （例如 `gateway-it:replay:USER_AUTH.LOGIN:1717430400000:nonce-1001`），
   并用 `TTL <key>` 观察剩余有效期。
3. 在 30 秒 TTL 内重复 `SET NX` 同一 key 即可手工复现"恰好一次成功"的语义。
4. 观察完成后删除输出的 marker 文件，测试会立即继续并清理容器；如果不删除，达到
   `gateway.it.manual-observation-seconds` 指定的时间后也会自动清理。

默认不设置该参数时不会等待，适用于 CI；IDE Debug 时可以只使用上述断点而不设置参数。

## 观察入口

- 手工观察模式输出的 Redis 地址、`redis-cli` 命令和 marker 文件
- `redis-cli` 的 `KEYS gateway-it*`、`TTL <key>` 输出
- `gateway-integration-tests/target/failsafe-reports/com.cloud.gateway.infrastructure.replay.redis.RedisReplayGuardIT.txt`
- Testcontainers/Redis 容器日志

## 预期结果

- 两个实例、10 个线程并发登记同一 evidence key：恰好 1 个成功，9 个失败，
  Redis 中存在 `gateway-it:replay:<evidence>` 键且带 TTL。
- 约 800ms 的短 TTL 到期、键消失后，同一 evidence key 可以再次登记成功。
- 过期或零 TTL 的 `expiresAt` 直接返回失败，Redis 中不产生键。
- `gateway-it` 与 `gateway-it-other` 两个命名空间可以各自成功登记同一 evidence key，
  且互不影响对方的重复登记结果。
