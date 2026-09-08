package com.cloud.gateway.domain.policy.capability;

import java.time.Duration;

public record CircuitBreakerPolicy(
        String ownerService,
        int failureRateThreshold,
        int slowCallRateThreshold,
        Duration slowCallDuration,
        int halfOpenCalls,
        Duration waitDuration
) {
    public CircuitBreakerPolicy {
        if (ownerService == null || ownerService.isBlank()) {
            throw new IllegalArgumentException("ownerService must not be blank");
        }
        if (failureRateThreshold < 1 || failureRateThreshold > 100
                || slowCallRateThreshold < 1 || slowCallRateThreshold > 100) {
            throw new IllegalArgumentException("rate thresholds must be within [1, 100]");
        }
        if (slowCallDuration == null || slowCallDuration.isNegative() || slowCallDuration.isZero()) {
            throw new IllegalArgumentException("slowCallDuration must be positive");
        }
        if (halfOpenCalls < 1 || waitDuration == null || waitDuration.isNegative()) {
            throw new IllegalArgumentException("halfOpenCalls must be positive and waitDuration non-negative");
        }
    }
}
