package com.cloud.gateway.domain.policy.capability;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 按最严执行原则评估限流规则：命中的多条规则全部执行，任一拒绝即拒绝；
 * 无法解析维度值时跳过该规则。纯领域决策，不依赖 Redis。
 */
public final class RateLimitEvaluator {

    private RateLimitEvaluator() {
    }

    public static RateLimitDecision evaluate(
            List<RateLimitPolicy> rules,
            RateLimitQuota quota,
            Map<RateLimitDimension, String> dimensionValues
    ) {
        Map<RateLimitDimension, RateLimitDecision> executed = new EnumMap<>(RateLimitDimension.class);
        for (RateLimitPolicy rule : rules) {
            String value = dimensionValues.get(rule.dimension());
            if (value == null || value.isBlank()) {
                continue;
            }
            if (executed.containsKey(rule.dimension())) {
                continue;
            }
            RateLimitDecision decision = quota.tryAcquire(rule, rule.dimension() + ":" + value);
            executed.put(rule.dimension(), decision);
        }
        Optional<RateLimitDecision> rejected = executed.values().stream()
                .filter(decision -> !decision.allowed())
                .max((a, b) -> b.retryAfter().compareTo(a.retryAfter()));
        return rejected.orElseGet(() -> executed.values().stream()
                .min(java.util.Comparator.comparingLong(RateLimitDecision::remaining))
                .orElseGet(() -> RateLimitDecision.allowed(null, null, Long.MAX_VALUE)));
    }
}
