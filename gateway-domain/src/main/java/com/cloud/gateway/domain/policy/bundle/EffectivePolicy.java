package com.cloud.gateway.domain.policy.bundle;
import com.cloud.gateway.domain.policy.capability.*;
import com.cloud.gateway.domain.policy.header.HeaderPolicy;
import java.util.List;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
public record EffectivePolicy(List<RateLimitRules> rateLimits, TimeoutPolicy timeout,
        CircuitBreakerPolicy circuitBreaker, CachePolicy cache, HeaderPolicy headers) {
    public EffectivePolicy {
        Require.notNull(rateLimits, DomainException::missingField);
        rateLimits.forEach(rule -> Require.notNull(rule, DomainException::missingField));
        rateLimits = List.copyOf(rateLimits);
    }
}
