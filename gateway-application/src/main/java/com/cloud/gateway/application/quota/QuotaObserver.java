package com.cloud.gateway.application.quota;

import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
import com.cloud.gateway.domain.policy.capability.RateLimitPolicy;

/** Final outcome per executed rule, including any selected local fallback. */
@FunctionalInterface
public interface QuotaObserver {
    void record(RateLimitPolicy rule, RateLimitDecision decision);

    static QuotaObserver noop() {
        return (rule, decision) -> { };
    }
}
