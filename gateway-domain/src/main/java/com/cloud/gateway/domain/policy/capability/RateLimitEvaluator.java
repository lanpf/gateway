package com.cloud.gateway.domain.policy.capability;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/** Pure applicability and aggregation rules; never invokes adapters or mutates execution records. */
public final class RateLimitEvaluator {
    private RateLimitEvaluator() { }

    public static List<ApplicableQuota> applicable(List<RateLimitRules> groups,
            Map<RateLimitDimension, String> dimensions, boolean ipOnly, Set<String> applied) {
        Require.notNull(groups, DomainException::missingField);
        Require.notNull(dimensions, DomainException::missingField);
        Require.notNull(applied, DomainException::missingField);
        Set<String> selected = new HashSet<>(applied);
        List<ApplicableQuota> applicable = new ArrayList<>();
        for (RateLimitRules group : groups) {
            Require.notNull(group, DomainException::missingField);
            for (RateLimitPolicy rule : group.rules()) {
                if (ipOnly && rule.dimension() != RateLimitDimension.IP) {
                    continue;
                }
                String value = dimensions.get(rule.dimension());
                if (StringUtils.isBlank(value) || !selected.add(rule.ruleId())) {
                    continue;
                }
                applicable.add(new ApplicableQuota(rule, value, group.onRedisFailure()));
            }
        }
        return List.copyOf(applicable);
    }

    public static RateLimitDecision aggregate(List<RateLimitDecision> decisions) {
        Require.notNull(decisions, DomainException::missingField);
        RateLimitDecision selected = RateLimitDecision.allowed(null, null, Long.MAX_VALUE);
        for (RateLimitDecision decision : decisions) {
            Require.notNull(decision, DomainException::missingField);
            if (decision.unavailable()) {
                selected = decision;
            } else if (!selected.unavailable()) {
                if (!decision.allowed() && (selected.allowed()
                        || decision.retryAfter().compareTo(selected.retryAfter()) > 0)) {
                    selected = decision;
                } else if (decision.allowed() && selected.allowed() && decision.remaining() < selected.remaining()) {
                    selected = decision;
                }
            }
        }
        return selected;
    }
}
