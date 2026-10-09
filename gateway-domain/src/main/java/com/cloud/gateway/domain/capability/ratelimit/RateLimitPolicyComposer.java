package com.cloud.gateway.domain.capability.ratelimit;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;

import com.cloud.gateway.domain.capability.*;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyComposer;

import java.util.List;

public final class RateLimitPolicyComposer implements CapabilityPolicyComposer<RateLimitConfig, EffectiveRateLimitPolicy> {
    public static final RateLimitPolicyComposer INSTANCE = new RateLimitPolicyComposer();

    @Override public CapabilityId id() { return BuiltInCapabilityCatalog.RATE_LIMIT; }
    @Override public Class<RateLimitConfig> configType() { return RateLimitConfig.class; }
    @Override public Class<EffectiveRateLimitPolicy> policyType() { return EffectiveRateLimitPolicy.class; }
    @Override public EffectiveRateLimitPolicy compose(List<RateLimitConfig> orderedConfigs) {
        return new EffectiveRateLimitPolicy(orderedConfigs);
    }

}
