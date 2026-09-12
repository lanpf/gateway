package com.cloud.gateway.domain.policy.capability;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

/** Stable rule identity survives snapshot and quota parameter updates. */
public record RateLimitPolicy(String ruleId, RateLimitDimension dimension, int capacity,
                              int refillPerSecond, RateLimitMode mode) {
    public RateLimitPolicy {
        ruleId = Require.notBlank(ruleId, DomainException::missingField).trim();
        Require.notNull(dimension, DomainException::missingField);
        Require.notNull(mode, DomainException::missingField);
        if (capacity <= 0 || refillPerSecond <= 0 || ruleId.length() > 128) {
            throw DomainException.invalidField();
        }
    }
}
