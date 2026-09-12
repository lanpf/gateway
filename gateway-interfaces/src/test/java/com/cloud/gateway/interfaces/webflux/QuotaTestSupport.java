package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.application.quota.QuotaCommandService;
import com.cloud.gateway.application.quota.QuotaObserver;
import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
import com.cloud.gateway.domain.policy.capability.RateLimitQuota;

/** Unit-test assembly using explicit fail-closed local quota. */
final class QuotaTestSupport {
    private QuotaTestSupport() { }

    static RequestQuotaGuard guard(RateLimitQuota quota) {
        return new RequestQuotaGuard(new QuotaCommandService(quota,
                (rule, key) -> RateLimitDecision.unavailable(rule.dimension(), key), QuotaObserver.noop()));
    }
}
