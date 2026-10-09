package com.cloud.gateway.domain.capability.ratelimit;

import com.cloud.framework.core.validation.Require;

import java.time.Duration;
import com.cloud.framework.core.error.FrameworkException;

public record RateLimitDecision(boolean allowed, boolean unavailable, RateLimitDimension dimension,
                                String key, long remaining, Duration retryAfter) {
    public RateLimitDecision {
        Require.notNull(retryAfter);
        if ((allowed && unavailable) || remaining < 0 || retryAfter.isNegative()
                || (allowed && !retryAfter.isZero()) || (unavailable && !retryAfter.isZero())) {
            throw FrameworkException.invalidObject();
        }
    }

    public static RateLimitDecision allowed(RateLimitDimension dimension, String key, long remaining) {
        return new RateLimitDecision(true, false, dimension, key, remaining, Duration.ZERO);
    }

    public static RateLimitDecision rejected(RateLimitDimension dimension, String key, Duration retryAfter) {
        return new RateLimitDecision(false, false, dimension, key, 0, retryAfter);
    }

    public static RateLimitDecision unavailable(RateLimitDimension dimension, String key) {
        return new RateLimitDecision(false, true, dimension, key, 0, Duration.ZERO);
    }
}
