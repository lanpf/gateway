package com.cloud.gateway.application.quota;

import com.cloud.gateway.domain.policy.capability.ApplicableQuota;
import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
import com.cloud.gateway.domain.policy.capability.RateLimitEvaluator;
import com.cloud.gateway.domain.policy.capability.RateLimitQuota;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/** Every applicable rule executes after denial too; there is no cross-key atomicity. */
@RequiredArgsConstructor
public final class QuotaCommandService {
    private final RateLimitQuota quota;
    private final LocalRateLimitQuota localQuota;
    private final QuotaObserver observer;

    /** applied is a mutable request-owned record shared serially across ingress phases. */
    public RateLimitDecision acquire(QuotaCommand command, Set<String> applied) {
        List<ApplicableQuota> applicable = RateLimitEvaluator.applicable(
                command.groups(), command.dimensions(), command.ipOnly(), applied);
        List<RateLimitDecision> decisions = new ArrayList<>();
        for (ApplicableQuota selected : applicable) {
            RateLimitDecision decision = Objects.requireNonNull(quota.tryAcquire(selected.rule(), selected.key()),
                    "quota decision must not be null");
            if (selected.shouldFallback(decision)) {
                decision = Objects.requireNonNull(localQuota.tryAcquire(selected.rule(), selected.key()),
                        "local quota decision must not be null");
            }
            applied.add(selected.rule().ruleId());
            decisions.add(decision);
            observer.record(selected.rule(), decision);
        }
        return RateLimitEvaluator.aggregate(decisions);
    }
}
