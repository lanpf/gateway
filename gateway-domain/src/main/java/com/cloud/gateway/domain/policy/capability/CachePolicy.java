package com.cloud.gateway.domain.policy.capability;

import com.cloud.gateway.domain.endpoint.EndpointId;
import java.time.Duration;

public record CachePolicy(EndpointId endpointId, Duration ttl, Duration staleWhileRevalidate) {
    public CachePolicy {
        if (endpointId == null) {
            throw new IllegalArgumentException("endpointId must not be null");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()
                || staleWhileRevalidate == null || staleWhileRevalidate.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive and staleWhileRevalidate non-negative");
        }
    }
}
