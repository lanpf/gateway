package com.cloud.gateway.domain.policy.capability;

import java.time.Duration;

public record TimeoutPolicy(String ownerService, Duration connect, Duration read) {
    public TimeoutPolicy {
        if (ownerService == null || ownerService.isBlank()) {
            throw new IllegalArgumentException("ownerService must not be blank");
        }
        if (connect == null || connect.isNegative() || read == null || read.isNegative()) {
            throw new IllegalArgumentException("connect and read timeouts must be non-negative");
        }
    }
}
