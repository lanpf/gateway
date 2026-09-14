package com.cloud.gateway.application.quota;

import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
import com.cloud.gateway.domain.policy.capability.RateLimitRule;

/** Bounded emergency quota; never claims a cluster-wide EXACT guarantee. */
@FunctionalInterface
public interface LocalRateLimitQuota {
    RateLimitDecision tryAcquire(RateLimitRule rule, String key);
}
