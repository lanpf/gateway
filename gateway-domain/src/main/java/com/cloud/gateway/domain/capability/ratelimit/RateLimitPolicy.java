package com.cloud.gateway.domain.capability.ratelimit;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

import java.util.List;
import java.util.Objects;

public record RateLimitPolicy(List<RateLimitRule> rules, RedisFailureMode failureMode) implements com.cloud.gateway.domain.capability.CapabilityConfig {
    public RateLimitPolicy {
        if (rules != null && rules.stream().anyMatch(Objects::isNull)) {
            throw DomainException.missingField();
        }
        rules = rules == null ? List.of() : List.copyOf(rules);
        Require.noDuplicates(rules.stream().map(RateLimitRule::ruleId).toList(), DomainException::invalidObjectField);
        failureMode = failureMode == null ? RedisFailureMode.CLOSED : failureMode;
    }

    public static RateLimitPolicy disabled() {
        return new RateLimitPolicy(List.of(), RedisFailureMode.CLOSED);
    }
    @Override
    public com.cloud.gateway.domain.capability.CapabilityId capabilityId() { return RateLimitConfigDefinition.ID; }
}
