package com.cloud.gateway.application.quota;

import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;

/** Final decision per executed rule, including any selected local fallback. */
@FunctionalInterface
public interface QuotaObserver {
    void record(RateLimitRule rule, RateLimitDecision decision);

    static QuotaObserver noop() {
        return (rule, decision) -> { };
    }
}
