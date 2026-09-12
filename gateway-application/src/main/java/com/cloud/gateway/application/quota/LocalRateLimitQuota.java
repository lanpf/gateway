package com.cloud.gateway.application.quota;

import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
import com.cloud.gateway.domain.policy.capability.RateLimitPolicy;

/** Bounded emergency quota; never claims a cluster-wide EXACT guarantee. */
@FunctionalInterface
public interface LocalRateLimitQuota {
    RateLimitDecision tryAcquire(RateLimitPolicy rule, String key);
}
