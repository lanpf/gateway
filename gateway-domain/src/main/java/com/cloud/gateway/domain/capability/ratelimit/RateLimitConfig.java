package com.cloud.gateway.domain.capability.ratelimit;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.capability.CapabilityConfig;
import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;

import java.util.List;

public record RateLimitConfig(List<RateLimitRule> rules, RedisFailureMode failureMode) implements CapabilityConfig {
    public RateLimitConfig {
        rules = NullSafe.copyOf(rules);
        Require.noDuplicates(rules, RateLimitRule::ruleId, () -> new DomainException(DomainError.ACCESS_SNAPSHOT_POLICY_INVALID));
        failureMode = failureMode == null ? RedisFailureMode.CLOSED : failureMode;
    }

    public static RateLimitConfig disabled() {
        return new RateLimitConfig(List.of(), RedisFailureMode.CLOSED);
    }

    @Override
    public CapabilityId id() { return BuiltInCapabilityCatalog.RATE_LIMIT; }
}
