package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.application.quota.LocalQuotaAcquirer;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Reuses the stable Redis bucket identity for the bounded per-instance token algorithm. */
public final class LocalTokenBucketQuotaAcquirer implements LocalQuotaAcquirer {
    private final RateLimitKeyResolver keyResolver;
    private final LocalTokenBucketFallback fallback;

    public LocalTokenBucketQuotaAcquirer(RateLimitKeyResolver keyResolver, RedisRateLimitProperties properties) {
        this(keyResolver, properties, System::nanoTime);
    }

    LocalTokenBucketQuotaAcquirer(RateLimitKeyResolver keyResolver, RedisRateLimitProperties properties, LongSupplier ticker) {
        this.keyResolver = Require.notNull(keyResolver);
        this.fallback = new LocalTokenBucketFallback(properties.getLocalMaxBuckets(), ticker);
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitRule rule, String key) {
        return fallback.tryAcquire(rule, key, keyResolver.bucket(rule, key));
    }
}
