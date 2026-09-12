package com.cloud.gateway.domain.policy.capability;

/** Synchronous quota port; callers must use a blocking-capable worker, not a reactive event loop. */
public interface RateLimitQuota {
    RateLimitDecision tryAcquire(RateLimitPolicy rule, String key);

}
