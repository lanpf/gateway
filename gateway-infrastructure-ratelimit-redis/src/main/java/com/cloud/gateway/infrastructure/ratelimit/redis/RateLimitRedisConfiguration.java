package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.gateway.domain.policy.capability.RateLimitQuota;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(RedisRateLimitProperties.class)
public class RateLimitRedisConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public RateLimitQuota rateLimitQuota(
            RedisTokenBucketLimiter limiter,
            StringRedisTemplate redisTemplate,
            RedisRateLimitProperties properties
    ) {
        return new RateLimitQuotaAdapter(limiter, redisTemplate, properties);
    }
}
