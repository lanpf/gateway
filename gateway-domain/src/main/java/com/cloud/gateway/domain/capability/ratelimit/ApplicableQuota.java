package com.cloud.gateway.domain.capability.ratelimit;

import com.cloud.gateway.domain.common.DomainException;

import com.cloud.framework.core.validation.Require;

/** One selected rule and its original dimension value, without execution state. */
public record ApplicableQuota(RateLimitRule rule, String key, RedisFailureMode failureMode) {
    public ApplicableQuota {
        Require.notNull(rule, DomainException::missingField);
        Require.notBlank(key, DomainException::invalidObjectField);
        Require.notNull(failureMode, DomainException::missingField);
    }

    public boolean shouldFallback(RateLimitDecision decision) {
        Require.notNull(decision, DomainException::missingField);
        return decision.unavailable() && failureMode == RedisFailureMode.OPEN_WITH_LOCAL;
    }
}
