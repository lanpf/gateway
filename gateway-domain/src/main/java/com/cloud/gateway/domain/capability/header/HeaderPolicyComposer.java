package com.cloud.gateway.domain.capability.header;

import com.cloud.framework.core.validation.Require;
import com.cloud.framework.core.util.NullSafe;
import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyComposer;

import java.util.List;

public final class HeaderPolicyComposer implements CapabilityPolicyComposer<HeaderConfig, EffectiveHeaderPolicy> {
    public static final HeaderPolicyComposer INSTANCE = new HeaderPolicyComposer();

    @Override public CapabilityId id() { return BuiltInCapabilityCatalog.HEADER_APPLY; }
    @Override public Class<HeaderConfig> configType() { return HeaderConfig.class; }
    @Override public Class<EffectiveHeaderPolicy> policyType() { return EffectiveHeaderPolicy.class; }
    @Override public EffectiveHeaderPolicy compose(List<HeaderConfig> orderedConfigs) {
        List<HeaderConfig> configs = NullSafe.copyOf(Require.notNull(orderedConfigs));
        return new EffectiveHeaderPolicy(configs.isEmpty() ? HeaderConfig.defaults() : orderedConfigs.get(configs.size() - 1));
    }
}
