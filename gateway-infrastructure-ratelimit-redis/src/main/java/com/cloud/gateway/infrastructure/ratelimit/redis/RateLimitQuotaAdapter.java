package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
import com.cloud.gateway.domain.policy.capability.RateLimitDimension;
import com.cloud.gateway.domain.policy.capability.RateLimitMode;
import com.cloud.gateway.domain.policy.capability.RateLimitPolicy;
import com.cloud.gateway.domain.policy.capability.RateLimitQuota;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 领域配额端口的 Redis 适配：按策略模式分发到精确 Lua 或本地预分配池。 */
public class RateLimitQuotaAdapter implements RateLimitQuota {
    private final RedisTokenBucketLimiter limiter;
    private final StringRedisTemplate redisTemplate;
    private final RedisRateLimitProperties properties;
    private final Map<String, PreallocatedTokenPool> pools = new ConcurrentHashMap<>();

    public RateLimitQuotaAdapter(
            RedisTokenBucketLimiter limiter,
            StringRedisTemplate redisTemplate,
            RedisRateLimitProperties properties
    ) {
        this.limiter = limiter;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitPolicy rule, String key) {
        if (rule.mode() == RateLimitMode.EXACT) {
            long[] result = limiter.tryAcquire(key, rule.capacity(), rule.refillPerSecond(), 1);
            return toDecision(rule, key, result, 1);
        }
        PreallocatedTokenPool pool = pools.computeIfAbsent(key,
                k -> new PreallocatedTokenPool(
                        redisTemplate, properties.getKeyPrefix(),
                        rule.capacity(), rule.refillPerSecond(), properties.getPreallocationBatch()));
        long[] result = pool.tryAcquire(key, 1);
        return toDecision(rule, key, result, 1);
    }

    private RateLimitDecision toDecision(RateLimitPolicy rule, String key, long[] result, int requested) {
        long remainingSeconds = Math.max(1, rule.capacity() / rule.refillPerSecond());
        if (result[0] == 1) {
            return RateLimitDecision.allowed(rule.dimension(), key, result[1]);
        }
        return RateLimitDecision.rejected(rule.dimension(), key, Duration.ofSeconds(remainingSeconds));
    }
}
