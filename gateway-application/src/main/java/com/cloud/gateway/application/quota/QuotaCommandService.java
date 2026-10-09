package com.cloud.gateway.application.quota;

import com.cloud.gateway.domain.capability.ratelimit.ApplicableQuota;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRuleId;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitEvaluator;
import com.cloud.gateway.application.quota.QuotaAcquirer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/** Every applicable rule executes after denial too; there is no cross-key atomicity. */
@RequiredArgsConstructor
public final class QuotaCommandService {
    private final QuotaAcquirer quota;
    private final LocalQuotaAcquirer localQuota;
    private final QuotaObserver observer;

    /** applied is a mutable request-owned record shared serially across ingress phases. */
    public RateLimitDecision acquire(QuotaCommand command, Set<RateLimitRuleId> applied) {
        List<ApplicableQuota> applicable = RateLimitEvaluator.applicable(
                command.groups(), command.dimensions(), command.ipOnly(), applied);
        return execute(applicable, applied);
    }

    public RateLimitDecision acquire(PlannedQuotaCommand command) {
        return execute(command.quotas(), new java.util.HashSet<>());
    }

    private RateLimitDecision execute(List<ApplicableQuota> applicable, Set<RateLimitRuleId> applied) {
        List<RateLimitDecision> decisions = new ArrayList<>();
        for (ApplicableQuota selected : applicable) {
            RateLimitDecision decision = Objects.requireNonNull(quota.tryAcquire(selected.rule(), selected.key()),
                    "quota denied must not be null");
            if (selected.shouldFallback(decision)) {
                decision = Objects.requireNonNull(localQuota.tryAcquire(selected.rule(), selected.key()),
                        "local quota denied must not be null");
            }
            applied.add(selected.rule().ruleId());
            decisions.add(decision);
            observer.record(selected.rule(), decision);
        }
        return RateLimitEvaluator.aggregate(decisions);
    }
}
