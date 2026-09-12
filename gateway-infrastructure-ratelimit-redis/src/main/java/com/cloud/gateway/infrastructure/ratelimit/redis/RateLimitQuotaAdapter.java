package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
import com.cloud.gateway.domain.policy.capability.RateLimitPolicy;
import com.cloud.gateway.domain.policy.capability.RateLimitQuota;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/** Synchronous Redis adapter; callers must move execution off the reactive event loop. */
@Slf4j
public final class RateLimitQuotaAdapter implements RateLimitQuota {
    private final RedisTokenBucketLimiter limiter;
    private final RateLimitKeyResolver keyResolver;
    private final AtomicBoolean degraded = new AtomicBoolean();

    public RateLimitQuotaAdapter(RedisTokenBucketLimiter limiter, RateLimitKeyResolver keyResolver) {
        this.limiter = Objects.requireNonNull(limiter);
        this.keyResolver = Objects.requireNonNull(keyResolver);
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitPolicy rule, String key) {
        String resolvedKey = keyResolver.bucket(rule, key);
        try {
            RedisTokenBucketLimiter.BucketDecision decision = limiter.tryAcquire(rule, resolvedKey);
            if (degraded.compareAndSet(true, false)) {
                log.info("Rate limit Redis recovered: scene=quota");
            }
            return decision.allowed()
                    ? RateLimitDecision.allowed(rule.dimension(), key, decision.remaining())
                    : RateLimitDecision.rejected(rule.dimension(), key, decision.retryAfter());
        } catch (DataAccessException exception) {
            if (degraded.compareAndSet(false, true)) {
                // Redis exception text can contain keys; deliberately do not log its payload.
                log.warn("Rate limit Redis unavailable: scene=quota failureType={}", exception.getClass().getSimpleName());
            }
            return RateLimitDecision.unavailable(rule.dimension(), key);
        }
    }
}
