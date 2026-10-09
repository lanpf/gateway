package com.cloud.gateway.application.quota;

import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;

/** Synchronous shared-quota port; callers must use a blocking-capable worker, not a reactive event loop. */
@FunctionalInterface
public interface QuotaAcquirer {
    RateLimitDecision tryAcquire(RateLimitRule rule, String key);

}
