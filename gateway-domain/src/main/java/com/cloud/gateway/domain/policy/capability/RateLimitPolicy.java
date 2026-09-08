package com.cloud.gateway.domain.policy.capability;

import java.time.Duration;

public record RateLimitPolicy(
        RateLimitDimension dimension,
        int capacity,
        int refillPerSecond,
        RateLimitMode mode,
        Duration preallocationBatchWindow
) {
    public RateLimitPolicy {
        if (dimension == null) {
            throw new IllegalArgumentException("dimension must not be null");
        }
        if (capacity <= 0 || refillPerSecond <= 0) {
            throw new IllegalArgumentException("capacity and refillPerSecond must be positive");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (mode == RateLimitMode.PREALLOCATED && preallocationBatchWindow == null) {
            preallocationBatchWindow = Duration.ofSeconds(1);
        }
    }

    public Duration preallocationBatchWindow() {
        return preallocationBatchWindow == null ? Duration.ofSeconds(1) : preallocationBatchWindow;
    }
}
