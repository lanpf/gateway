package com.cloud.gateway.domain.policy.capability;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

/** One selected rule and its original dimension value, without execution state. */
public record ApplicableQuota(RateLimitRule rule, String key, RedisFailurePolicy failurePolicy) {
    public ApplicableQuota {
        Require.notNull(rule, DomainException::missingField);
        Require.notBlank(key, DomainException::missingField);
        Require.notNull(failurePolicy, DomainException::missingField);
    }

    public boolean shouldFallback(RateLimitDecision decision) {
        Require.notNull(decision, DomainException::missingField);
        return decision.unavailable() && failurePolicy == RedisFailurePolicy.OPEN_WITH_LOCAL;
    }
}
