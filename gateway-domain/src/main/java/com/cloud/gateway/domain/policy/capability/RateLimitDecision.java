package com.cloud.gateway.domain.policy.capability;

import java.time.Duration;

public record RateLimitDecision(
        boolean allowed,
        RateLimitDimension dimension,
        String key,
        long remaining,
        Duration retryAfter
) {
    public static RateLimitDecision allowed(RateLimitDimension dimension, String key, long remaining) {
        return new RateLimitDecision(true, dimension, key, remaining, Duration.ZERO);
    }

    public static RateLimitDecision rejected(RateLimitDimension dimension, String key, Duration retryAfter) {
        return new RateLimitDecision(false, dimension, key, 0, retryAfter);
    }
}
