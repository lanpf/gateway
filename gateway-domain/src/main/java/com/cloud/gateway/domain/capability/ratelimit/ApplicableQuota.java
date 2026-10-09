package com.cloud.gateway.domain.capability.ratelimit;


import com.cloud.framework.core.validation.Require;

/** One selected rule and its original dimension value, without execution state. */
public record ApplicableQuota(RateLimitRule rule, String key, RedisFailureMode failureMode) {
    public ApplicableQuota {
        Require.allNotNull(rule, failureMode);
        Require.notBlank(key);
    }

    public boolean shouldFallback(RateLimitDecision decision) {
        return Require.notNull(decision).unavailable() && failureMode == RedisFailureMode.OPEN_WITH_LOCAL;
    }
}
