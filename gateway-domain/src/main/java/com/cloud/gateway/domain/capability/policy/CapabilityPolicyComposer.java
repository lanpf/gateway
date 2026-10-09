package com.cloud.gateway.domain.capability.policy;

import com.cloud.gateway.domain.capability.CapabilityConfig;
import com.cloud.gateway.domain.capability.CapabilityId;

import java.util.List;

/**
 * Composes one effective policy from ordered configurations.
 * Only type information and policy configuration belong here; no bundle, validation hook or slot dependency.
 */
public interface CapabilityPolicyComposer<C extends CapabilityConfig, P> {
    CapabilityId id();

    Class<C> configType();
    Class<P> policyType();
    /** Least-specific first; empty input yields a default policy, never null. Elements must be non-null. */
    P compose(List<C> orderedConfigs);
}
