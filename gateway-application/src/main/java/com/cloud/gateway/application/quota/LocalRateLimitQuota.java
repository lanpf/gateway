package com.cloud.gateway.application.quota;

import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;

/** Bounded emergency quota; never claims a cluster-wide EXACT guarantee. */
@FunctionalInterface
public interface LocalRateLimitQuota {
    RateLimitDecision tryAcquire(RateLimitRule rule, String key);
}
