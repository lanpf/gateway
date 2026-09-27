package com.cloud.gateway.domain.capability.ratelimit;

/** Synchronous quota port; callers must use a blocking-capable worker, not a reactive event loop. */
public interface RateLimitQuota {
    RateLimitDecision tryAcquire(RateLimitRule rule, String key);

}
