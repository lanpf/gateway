package com.cloud.gateway.domain.policy.capability;

import java.util.List;

public record RateLimitRules(List<RateLimitPolicy> rules, RedisFailurePolicy onRedisFailure) {
    public RateLimitRules {
        rules = rules == null ? List.of() : List.copyOf(rules);
        onRedisFailure = onRedisFailure == null ? RedisFailurePolicy.OPEN_WITH_LOCAL : onRedisFailure;
    }

    public static RateLimitRules disabled() {
        return new RateLimitRules(List.of(), RedisFailurePolicy.OPEN_WITH_LOCAL);
    }
}
