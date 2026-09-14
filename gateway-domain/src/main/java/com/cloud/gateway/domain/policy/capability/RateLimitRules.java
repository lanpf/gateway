package com.cloud.gateway.domain.policy.capability;

import com.cloud.gateway.domain.common.DomainException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record RateLimitRules(List<RateLimitRule> rules, RedisFailurePolicy onRedisFailure) {
    public RateLimitRules {
        if (rules != null && rules.stream().anyMatch(Objects::isNull)) {
            throw DomainException.missingField();
        }
        rules = rules == null ? List.of() : List.copyOf(rules);
        HashSet<RateLimitRuleId> ruleIds = new HashSet<>();
        for (RateLimitRule rule : rules) {
            if (!ruleIds.add(rule.ruleId())) {
                throw DomainException.invalidField();
            }
        }
        onRedisFailure = onRedisFailure == null ? RedisFailurePolicy.CLOSED : onRedisFailure;
    }

    public static RateLimitRules disabled() {
        return new RateLimitRules(List.of(), RedisFailurePolicy.CLOSED);
    }
}
