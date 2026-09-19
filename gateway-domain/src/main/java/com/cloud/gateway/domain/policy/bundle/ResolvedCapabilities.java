package com.cloud.gateway.domain.policy.bundle;

import com.cloud.gateway.domain.policy.capability.*;
import com.cloud.gateway.domain.policy.header.HeaderPolicy;
import java.util.List;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

/**
 * The capability nodes that apply to one request, after the composition was resolved against that
 * request's endpoint and identity. Single-valued nodes carry the most specific matching binding's
 * value; rate-limit groups accumulate, one per bundle.
 *
 * <p>A resolved policy always carries a Header plan: {@link CapabilityResolver#resolve} substitutes the
 * default before construction, which is why the invariant can be asserted here.
 */
public record ResolvedCapabilities(List<RateLimitRules> rateLimits, TimeoutPolicy timeout,
        CircuitBreakerPolicy circuitBreaker, CachePolicy cache, HeaderPolicy headers) {
    public ResolvedCapabilities {
        Require.notNull(rateLimits, DomainException::missingField);
        rateLimits.forEach(rule -> Require.notNull(rule, DomainException::missingField));
        rateLimits = List.copyOf(rateLimits);
        Require.notNull(headers, DomainException::missingField);
    }
}
