# 能力链设计（一期：限流 / 熔断与超时 / 缓存 + 执行骨架）

本文是能力链（Capability Chain）一期的详细设计。基线决策：未知能力容忍跳过；处理器顺序固定注册表；Rewrite/ModifyResponse 无脚本；能力配置三层覆盖采用节点整体覆盖语义。对标 Apache ShenYu 插件家族，实现为编译期内建 handler + 策略快照驱动开关。

## 1. 快照 schema 演进（schemaVersion 2）

`IngressPolicySnapshot` 新增可选 `capabilities` 节；缺省 = 全部能力关闭。示例：

```json
{
  "schemaVersion": 2,
  "capabilities": {
    "rate-limit": {
      "onRedisFailure": "open-with-local",
      "rules": [
        { "dimension": "subject", "capacity": 1000, "refillPerSecond": 50, "mode": "preallocated" },
        { "dimension": "channel", "capacity": 50000, "refillPerSecond": 2000, "mode": "preallocated" },
        { "dimension": "ip", "capacity": 300, "refillPerSecond": 30, "mode": "preallocated" }
      ]
    },
    "circuit-breaker": {
      "rules": [ { "ownerService": "user-auth", "failureRateThreshold": 50,
                   "slowCallDuration": "2s", "slowCallRateThreshold": 80,
                   "halfOpenCalls": 10, "waitDuration": "30s" } ]
    },
    "timeout": {
      "rules": [ { "ownerService": "user-auth", "connect": "1s", "read": "5s" } ]
    },
    "cache": {
      "rules": [ { "endpointId": "ep-123", "ttl": "60s", "staleWhileRevalidate": "300s" } ]
    }
  }
}
```

规则：

- 能力键必须是注册表已知类型；未知键由节点 **WARN + 跳过**（滚动升级容忍），发布面（治理 API）**拒绝**未知键（两道防线语义不同：节点侧保可用，发布侧保规范）。
- 反序列化为容错读取：`SnapshotCapabilities` 按类型化字段绑定，未知键丢弃并计数上报。
- 合并语义：`capabilities` 节**整体覆盖**全局同名能力（`config/application.yml` 的同名结构），不做字段级深合并。

## 2. 领域模型（gateway-domain/policy/capability/）

值对象（全部 record）：

| 对象 | 字段 |
| --- | --- |
| `RateLimitPolicy` | `Dimension dimension`（SUBJECT/APP/CHANNEL/IP）、`int capacity`、`int refillPerSecond`、`Mode mode`（PREALLOCATED/EXACT） |
| `RateLimitRules` | `List<RateLimitPolicy> rules`、`RedisFailurePolicy onRedisFailure`（OPEN_WITH_LOCAL/CLOSED） |
| `CircuitBreakerPolicy` | `String ownerService`、`int failureRateThreshold`、`Duration slowCallDuration`、`int slowCallRateThreshold`、`int halfOpenCalls`、`Duration waitDuration` |
| `TimeoutPolicy` | `String ownerService`、`Duration connect`、`Duration read` |
| `CachePolicy` | `EndpointId endpointId`、`Duration ttl`、`Duration staleWhileRevalidate` |
| `SnapshotCapabilities` | `RateLimitRules rateLimit`、`List<CircuitBreakerPolicy> breaker`、`List<TimeoutPolicy> timeout`、`List<CachePolicy> cache`（可选节，缺省全关） |

决策纯函数（不依赖基础设施）：

- `RateLimitEvaluator.evaluate(RateLimitRules, RequestContext)`：维度解析（SUBJECT 取 `AuthenticatedSubject.subjectId()`、APP 取 ClientAppId、CHANNEL 取渠道码、IP 取远端地址）；多条命中按**最严执行**（任一拒绝即拒，返回剩余量最小者）；产出 `RateLimitDecision(allowed, key, remaining, retryAfter)`。
- `CacheKeyResolver.resolve(CachePolicy, request)`：`method + path + 规范化 query + channel`，**不含主体身份**。

## 3. 基础设施执行器

### 3.1 限流（新 module `gateway-infrastructure-ratelimit-redis`）

- `RedisTokenBucketLimiter`：EXACT 模式每请求执行 Lua（HGET tokens/ts → 补币 → 扣减 → HMSET + EXPIRE；时间取 Redis `TIME`，消除实例时钟偏移）：
  ```lua
  local key = KEYS[1]
  local capacity, refill, requested = tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3])
  local t = redis.call('TIME')
  local now = t[1] + t[2] / 1000000
  local bucket = redis.call('HMGET', key, 'tokens', 'ts')
  local tokens = tonumber(bucket[1]) or capacity
  local ts = tonumber(bucket[2]) or now
  tokens = math.min(capacity, tokens + math.max(0, now - ts) * refill) - requested
  local ttl = math.ceil(capacity / refill) * 2
  if tokens < 0 then
    redis.call('HMSET', key, 'tokens', 0, 'ts', now)
    redis.call('EXPIRE', key, ttl)
    return {0, 0}
  end
  redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
  redis.call('EXPIRE', key, ttl)
  return {1, math.floor(tokens)}
  ```
- `PreallocatedTokenPool`：PREALLOCATED 模式下每 (key, 实例) 维护本地余量；低于低水位（batch/2）异步执行批量 Lua 取 `min(batch, 余量)`；本地即时判定。
- 失败语义：`OPEN_WITH_LOCAL`——Redis 异常切实例本地计数器继续限流 + ERROR 告警（一次性）；`CLOSED`——直接 503。
- 拒绝响应：429 + `Retry-After`（按 refill 估算）+ 指标（维度、规则）。

### 3.2 熔断/超时

- Resilience4J（`resilience4j-reactor`）实例本地：`CircuitBreakerRegistry` 按 `ownerService` 持有；策略发布事件触发对应条目重建（`changeConfig` 不跨版本保证，采用重建替换）。
- 超时：`TimeoutPolicy` 以 `resilience4j-reactor` 的 `Timeout` 包裹同一下游调用。
- 降级动作：open 时策略联动缓存 `staleWhileRevalidate` 有副本则回 stale（标记 `X-Served-By-Stale: true`），否则 503 + `Retry-After`。
- 状态迁移发 Micrometer 指标（breaker.state、slow/fail 计数，按 ownerService 打标）。

### 3.3 缓存

- Caffeine（实例本地，`gateway-infrastructure` 内新增 `cache` 包；无外部适配器不拆 module）。
- 回源 single-flight：同 key 在途合并（Caffeine `AsyncCache` 原生语义）。
- SWR：TTL 过期后进入 stale 窗口，命中 stale 返回并触发异步回源；熔断 open 时 stale 窗口视为有效副本。
- 写缓存仅缓存主体无关响应；`Cache-Control: private|no-store` 一票否决。
- 清除：策略发布动作可携带按 ClientApp/endpoint 的定向清除；TTL 兜底。

## 4. 过滤器装配（gateway-interfaces/webflux/capability/）

固定顺序注册表（对齐既有过滤器编号）：

| order | WebFilter | 说明 |
| --- | --- | --- |
| -200 | ReplayGuard（已有，编号对齐） | 防重放 |
| -100 | SubjectAuth（已有） | 主体认证 |
| -50 | RateLimitWebFilter | 端点匹配前执行 IP/CHANNEL 维度；匹配后执行 SUBJECT/APP 维度 |
| 0 | CacheLookupWebFilter | 命中（含 stale）即返回 |
| — | delivery（已有下游调用） | 以 CircuitBreaker + Timeout 包裹 |
| 60 | CacheStoreWebFilter | 回源写缓存 + SWR 刷新 |
| 100 | AccessLogWebFilter（预留） | 访问日志（含限流/熔断决策） |

- 端点未匹配（404 前的未知路径）：能力链跳过，仅 RateLimit 的 IP 维度生效。
- 每过滤器从 `ResolvedEndpointContext`（端点匹配产物）读取 `SnapshotCapabilities`；未配置 = 透传。

## 5. 发布校验（治理 API）

- 能力键 ∈ 注册表；未知键**拒绝发布**。
- 数值域校验：capacity > 0、refill > 0、failureRateThreshold/slowCallRateThreshold ∈ [1,100]、duration > 0、cache TTL ≥ 1s。
- `schemaVersion` 必须 = 2；`minGatewayVersion`（可选）≤ 集群最低运行版本。
- 发布/回滚沿用既有状态机；回滚 = 重新分发上一版快照（能力配置随之回滚）。

## 6. 测试与验收

- 单测：RateLimitEvaluator 最严执行与键解析、容错反序列化（未知能力跳过 + 计数）、SnapshotCapabilities 合并语义。
- IT（`gateway-integration-tests`，Testcontainers Redis + MySQL）：EXACT/PREALLOCATED 限流精度、Redis 故障切换（open-with-local/closed）、SWR 行为、熔断状态机迁移、发布→限流生效→回滚→恢复的全链路。
- 契约：三种策略能力均以快照发布驱动，验证回滚后行为恢复。

## 7. 实施切分

1. 领域模型 + 评估器 + 单测（纯领域，无依赖）。
2. `ratelimit-redis` module（Lua + 预分配 + 失败语义）+ IT。
3. 快照 schema v2 + 容错反序列化 + 发布校验。
4. 过滤器装配 + 缓存 + 熔断/超时接入 + IT。
5. 文档（RESPONSIBILITIES 能力清单更新 + 本文档转正式）。
