package com.cloud.gateway.domain.capability.ratelimit;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;

import java.util.List;

/** Accumulated matched configurations; each group retains its own Redis failure mode. */
public record EffectiveRateLimitPolicy(List<RateLimitConfig> configs) {
    public EffectiveRateLimitPolicy {
        configs = NullSafe.copyOf(Require.notNull(configs));
    }
}
