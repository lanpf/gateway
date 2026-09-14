package com.cloud.gateway.domain.policy.capability;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

/** Stable rule identity survives snapshot and quota parameter updates. */
public record RateLimitRule(RateLimitRuleId ruleId, RateLimitDimension dimension, int capacity,
                            int refillPerSecond, RateLimitMode mode) {
    public RateLimitRule {
        Require.notNull(ruleId, DomainException::missingField);
        Require.notNull(dimension, DomainException::missingField);
        Require.notNull(mode, DomainException::missingField);
        if (capacity <= 0 || refillPerSecond <= 0 || ruleId.value().length() > 128) {
            throw DomainException.invalidField();
        }
    }

    public RateLimitRule(String ruleId, RateLimitDimension dimension, int capacity,
                         int refillPerSecond, RateLimitMode mode) {
        this(new RateLimitRuleId(ruleId), dimension, capacity, refillPerSecond, mode);
    }
}
