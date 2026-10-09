package com.cloud.gateway.domain.capability.resilience;

import com.cloud.framework.core.validation.Require;
import com.cloud.framework.core.util.NullSafe;
import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyComposer;

import java.util.List;

public final class TimeoutPolicyComposer implements CapabilityPolicyComposer<TimeoutConfig, EffectiveTimeoutPolicy> {
    public static final TimeoutPolicyComposer INSTANCE = new TimeoutPolicyComposer();

    @Override public CapabilityId id() { return BuiltInCapabilityCatalog.TIMEOUT; }
    @Override public Class<TimeoutConfig> configType() { return TimeoutConfig.class; }
    @Override public Class<EffectiveTimeoutPolicy> policyType() { return EffectiveTimeoutPolicy.class; }
    @Override public EffectiveTimeoutPolicy compose(List<TimeoutConfig> orderedConfigs) {
        List<TimeoutConfig> configs = NullSafe.copyOf(Require.notNull(orderedConfigs));
        return new EffectiveTimeoutPolicy(configs.isEmpty() ? null : configs.get(configs.size() - 1));
    }
}
