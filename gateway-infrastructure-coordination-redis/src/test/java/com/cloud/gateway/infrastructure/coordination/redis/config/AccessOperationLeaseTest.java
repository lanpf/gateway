package com.cloud.gateway.infrastructure.coordination.redis.config;

import com.cloud.framework.core.error.FrameworkException;
import com.cloud.framework.starter.lock.redis.RedisLockProperties;
import com.cloud.gateway.infrastructure.coordination.redis.AccessRollbackStateKeyResolver;
import com.cloud.gateway.infrastructure.policy.config.AccessOperationProperties;
import java.time.Duration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AccessOperationLeaseTest {
    @Test
    void shouldRequireTheLeaseToCoverBudgetBlockingTimeAndMarginEvenWithRenewal() {
        var locks = new RedisLockProperties();
        locks.setAutoRenewal(true);
        var configuration = new RedisPolicyCoordinationAutoConfiguration();
        var redis = mock(StringRedisTemplate.class);
        var keys = mock(AccessRollbackStateKeyResolver.class);
        var budget = new AccessOperationProperties();
        var redisProperties = new RedisProperties();
        redisProperties.setTimeout(Duration.ofSeconds(5));
        redisProperties.setConnectTimeout(Duration.ofSeconds(2));
        assertThrows(FrameworkException.class, () -> configuration.policyRollbackState(redis, keys, locks, budget, redisProperties));
        locks.setLeaseTime(Duration.ofSeconds(90));
        assertDoesNotThrow(() -> configuration.policyRollbackState(redis, keys, locks, budget, redisProperties));
    }
    @Test
    void shouldRejectUnboundedOrOversizedRedisCalls() {
        var redis = new RedisProperties();
        var budget = new AccessOperationProperties();
        assertThrows(FrameworkException.class,
                () -> RedisPolicyCoordinationAutoConfiguration.validateBlockingLimits(redis, budget));
        redis.setTimeout(Duration.ofSeconds(9));
        redis.setConnectTimeout(Duration.ofSeconds(2));
        assertThrows(FrameworkException.class,
                () -> RedisPolicyCoordinationAutoConfiguration.validateBlockingLimits(redis, budget));
    }
}
