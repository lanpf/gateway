package com.cloud.gateway.domain.policy.bundle;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.policy.capability.*;
import com.cloud.gateway.domain.policy.header.HeaderPolicy;

public record PolicyBundle(
        PolicyBundleId bundleId,
        RateLimitRules rateLimit,
        TimeoutPolicy timeout,
        CircuitBreakerPolicy circuitBreaker,
        CachePolicy cache,
        HeaderPolicy headers) {
    public PolicyBundle {
        Require.notNull(bundleId, DomainException::missingField);
    }

    public boolean competesWith(PolicyBundle other) {
        return timeout != null && other.timeout != null
                || circuitBreaker != null && other.circuitBreaker != null
                || cache != null && other.cache != null
                || headers != null && other.headers != null;
    }
}
