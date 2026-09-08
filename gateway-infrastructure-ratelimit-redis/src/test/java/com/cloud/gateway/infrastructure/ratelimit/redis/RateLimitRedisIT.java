package com.cloud.gateway.infrastructure.ratelimit.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Redis 令牌桶契约：精确模式与预分配模式，真实 Redis 验证。 */
@Testcontainers
class RateLimitRedisIT {
    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.4.4")
            .withExposedPorts(6379);

    private StringRedisTemplate template() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        return new StringRedisTemplate(factory);
    }

    @Test
    void shouldAllowUpToCapacityThenRejectUnderExactMode() {
        var limiter = new RedisTokenBucketLimiter(template(), new RedisRateLimitProperties());
        String key = "it:" + System.nanoTime();

        for (int i = 0; i < 3; i++) {
            long[] result = limiter.tryAcquire(key, 3, 1, 1);
            assertTrue(result[0] == 1, "request " + i + " must be allowed within capacity");
        }
        assertFalse(limiter.tryAcquire(key, 3, 1, 1)[0] == 1);
    }

    @Test
    void shouldServePreallocatedPoolAcrossAdapterModes() {
        var template = template();
        var properties = new RedisRateLimitProperties();
        properties.setPreallocationBatch(2);
        var limiter = new RedisTokenBucketLimiter(template, properties);
        var adapter = new RateLimitQuotaAdapter(limiter, template, properties);
        var rule = new com.cloud.gateway.domain.policy.capability.RateLimitPolicy(
                com.cloud.gateway.domain.policy.capability.RateLimitDimension.SUBJECT,
                2, 1, com.cloud.gateway.domain.policy.capability.RateLimitMode.PREALLOCATED, null);
        String key = "it-pre:" + System.nanoTime();

        assertTrue(adapter.tryAcquire(rule, key).allowed());
        assertTrue(adapter.tryAcquire(rule, key).allowed());
        assertFalse(adapter.tryAcquire(rule, key).allowed());
    }
}
