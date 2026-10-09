package com.cloud.gateway.domain.capability.ratelimit;

import com.cloud.framework.core.util.NullSafe;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Pure applicability and aggregation rules; never invokes adapters or mutates execution records. */
public final class RateLimitEvaluator {
    private RateLimitEvaluator() { }

    public static List<ApplicableQuota> applicable(
            List<RateLimitConfig> configs,
            Map<RateLimitDimension, String> dimensions,
            boolean ipOnly,
            Set<RateLimitRuleId> applied) {
        Set<RateLimitRuleId> selected = new HashSet<>(NullSafe.copyOf(applied));

        List<ApplicableQuota> applicable = new ArrayList<>();
        NullSafe.forEach(configs, config -> {
            NullSafe.forEach(config.rules(), rule -> {
                if (ipOnly && rule.dimension() != RateLimitDimension.IP) {
                    return;
                }
                String value = dimensions.get(rule.dimension());
                if (StringUtils.isBlank(value) || !selected.add(rule.ruleId())) {
                    return;
                }
                applicable.add(new ApplicableQuota(rule, value, config.failureMode()));
            });
        });

        return List.copyOf(applicable);
    }

    public static RateLimitDecision aggregate(List<RateLimitDecision> decisions) {
        AtomicReference<RateLimitDecision> selected = new AtomicReference<>(RateLimitDecision.allowed(null, null, Long.MAX_VALUE));
        NullSafe.forEach(decisions, decision -> {
            if (decision.unavailable()) {
                selected.set(decision);
            } else if (!selected.get().unavailable()) {
                if (!decision.allowed() &&
                        (selected.get().allowed() || decision.retryAfter().compareTo(selected.get().retryAfter()) > 0)) {
                    selected.set(decision);
                } else if (decision.allowed() &&
                        selected.get().allowed() && decision.remaining() < selected.get().remaining()) {
                    selected.set(decision);
                }
            }
        });
        return selected.get();
    }
}
