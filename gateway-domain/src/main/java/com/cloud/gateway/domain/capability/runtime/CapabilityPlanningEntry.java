package com.cloud.gateway.domain.capability.runtime;

import com.cloud.framework.core.validation.Require;
import java.util.List;

/** Request-owned pairing. Configuration must be immutable; the effective policy itself is always present. */
public record CapabilityPlanningEntry<P>(PlannableCapability<P> capability, P policy) {
    public CapabilityPlanningEntry {
        Require.allNotNull(capability, policy);
    }

    List<? extends CapabilityUnit> units(CapabilityContext context) {
        return capability.units(policy, context);
    }
}
