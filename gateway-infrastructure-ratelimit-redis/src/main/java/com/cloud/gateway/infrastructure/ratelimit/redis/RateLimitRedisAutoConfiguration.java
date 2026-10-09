package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.framework.core.naming.NamespaceResolver;
import com.cloud.framework.core.naming.NamespacedResourceNameResolver;
import com.cloud.gateway.application.quota.QuotaAcquirer;
import com.cloud.gateway.application.quota.LocalQuotaAcquirer;
import com.cloud.gateway.application.quota.QuotaCommandService;
import com.cloud.gateway.application.quota.QuotaObserver;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@EnableConfigurationProperties(RedisRateLimitProperties.class)
public class RateLimitRedisAutoConfiguration {
    @Bean
    RateLimitKeyResolver rateLimitKeyResolver(RedisRateLimitProperties properties,
                                             NamespaceResolver namespaceResolver) {
        return new RateLimitKeyResolver(new NamespacedResourceNameResolver(namespaceResolver, properties));
    }

    @Bean
    RedisTokenBucketLimiter redisTokenBucketLimiter(StringRedisTemplate redisTemplate) {
        return new RedisTokenBucketLimiter(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(QuotaAcquirer.class)
    QuotaAcquirer rateLimitQuota(RedisTokenBucketLimiter limiter, RateLimitKeyResolver keyResolver,
                                  RedisRateLimitProperties properties) {
        return new RedisQuotaAcquirer(limiter, keyResolver, properties.getAcquireTimeout());
    }

    @Bean
    @ConditionalOnMissingBean(LocalQuotaAcquirer.class)
    LocalQuotaAcquirer localQuotaAcquirer(RateLimitKeyResolver keyResolver, RedisRateLimitProperties properties) {
        return new LocalTokenBucketQuotaAcquirer(keyResolver, properties);
    }

    @Bean
    @ConditionalOnMissingBean(QuotaObserver.class)
    QuotaObserver quotaObserver(ObjectProvider<MeterRegistry> registries) {
        MeterRegistry registry = registries.getIfUnique();
        return registry == null ? QuotaObserver.noop() : new MicrometerQuotaObserver(registry);
    }

    @Bean
    @ConditionalOnMissingBean(QuotaCommandService.class)
    QuotaCommandService quotaCommandService(QuotaAcquirer quota, LocalQuotaAcquirer local, QuotaObserver observer) {
        return new QuotaCommandService(quota, local, observer);
    }
}
