package com.cloud.gateway.domain.capability.runtime;

import com.cloud.framework.core.error.FrameworkException;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;
import com.cloud.gateway.domain.capability.resilience.EffectiveTimeoutPolicy;

import java.util.List;
import java.util.Set;

/**
 * The downstream timeout budget, planned before forwarding. A configured effective policy produces
 * one unit; an unconfigured policy produces none.
 *
 * <p>Enforcing the budget is the transport's job, not the domain's — the unit declares it and the
 * outcome hands it forward unchanged.
 */
public final class TimeoutCapability implements PlannableCapability<EffectiveTimeoutPolicy> {
    public static final CapabilityId ID = BuiltInCapabilityCatalog.TIMEOUT;

    @Override
    public CapabilityId id() { return ID; }

    @Override
    public Set<CapabilitySlot> slots() { return Set.of(CapabilitySlot.FORWARD); }

    @Override
    public CapabilityRepeat repeat() { return CapabilityRepeat.ONCE_PER_REQUEST; }

    @Override
    public List<Unit> units(EffectiveTimeoutPolicy policy, CapabilityContext context) {
        Require.notNull(policy);
        return policy.configured() ? List.of(new Unit(policy)) : List.of();
    }

    public record Unit(EffectiveTimeoutPolicy policy) implements CapabilityUnit {
        public Unit {
            Require.notNull(policy);
            if (!policy.configured()) {
                throw FrameworkException.invalidObject();
            }
        }
        @Override
        public String unitId() { return ID.value(); }
    }

    public record Outcome(EffectiveTimeoutPolicy policy) implements CapabilityOutcome {
        public Outcome {
            Require.notNull(policy);
            if (!policy.configured()) {
                throw FrameworkException.invalidObject();
            }
        }
        @Override
        public Disposition disposition() { return Disposition.CONTINUE; }

    }
}
