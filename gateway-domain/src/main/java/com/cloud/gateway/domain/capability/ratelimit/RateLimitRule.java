package com.cloud.gateway.domain.capability.ratelimit;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;

/** Stable rule identity survives snapshot and quota parameter updates. */
public record RateLimitRule(RateLimitRuleId ruleId, RateLimitDimension dimension, int capacity,
                            int refillPerSecond, RateLimitMode mode) {
    public RateLimitRule {
        Require.allNotNull(ruleId, dimension, mode);
        if (capacity <= 0 || refillPerSecond <= 0 || ruleId.value().length() > 128) {
            throw new DomainException(DomainError.ACCESS_SNAPSHOT_POLICY_INVALID);
        }
    }

    public RateLimitRule(String ruleId, RateLimitDimension dimension, int capacity,
                         int refillPerSecond, RateLimitMode mode) {
        this(new RateLimitRuleId(ruleId), dimension, capacity, refillPerSecond, mode);
    }
}
