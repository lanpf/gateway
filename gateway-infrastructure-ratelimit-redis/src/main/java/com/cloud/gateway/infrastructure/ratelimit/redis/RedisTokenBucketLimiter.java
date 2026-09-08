package com.cloud.gateway.infrastructure.ratelimit.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/** EXACT 模式的 Redis 令牌桶：取令牌与扣减在 Lua 内原子完成，时间取 Redis TIME。 */
@Component
public class RedisTokenBucketLimiter {
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>();

    static {
        SCRIPT.setLocation(new ClassPathResource("ratelimit_token_bucket.lua"));
        SCRIPT.setResultType(List.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public RedisTokenBucketLimiter(StringRedisTemplate redisTemplate, RedisRateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = properties.getKeyPrefix();
    }

    /** @return long[]{allowed(0/1), remaining} */
    public long[] tryAcquire(String key, int capacity, int refillPerSecond, int requested) {
        List<?> result = redisTemplate.execute(
                SCRIPT,
                List.of(keyPrefix + key),
                String.valueOf(capacity),
                String.valueOf(refillPerSecond),
                String.valueOf(requested));
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("rate limit script returned no result");
        }
        return new long[]{(long) result.get(0), (long) result.get(1)};
    }
}
