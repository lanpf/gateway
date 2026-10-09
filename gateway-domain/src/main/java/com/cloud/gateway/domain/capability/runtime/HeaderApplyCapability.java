package com.cloud.gateway.domain.capability.runtime;

import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;


import com.cloud.framework.core.validation.Require;

import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.header.EffectiveHeaderPolicy;

import java.util.List;
import java.util.Set;

/**
 * Applying the settled header plan to the upstream call: clear every protected field, then write the
 * verified results. A single-valued capability -- one complete plan, no alternatives to choose
 * between -- and it runs in {@code FORWARD}, the last slot before the call is issued.
 *
 * <p>Like the timeout budget, the plan is a <em>declaration</em>: the domain decides what the plan
 * is and that it must be applied, while mutating the transport's request belongs to the adapter that
 * owns the request. Execution therefore only makes the plan visible as an outcome; the filter applies
 * it. That split is what keeps this capability out of the HTTP layer while still letting it
 * participate in the fixed ordering and de-duplication every other capability gets.
 *
 * <p>This is a security-critical step, not an optimisation: a request that reaches the downstream
 * without the plan applied carries whatever the caller sent, including fields the gateway is supposed
 * to have stripped. See {@code IngressAdmissionGlobalFilter}, which fails closed when the plan is
 * absent rather than forwarding without it.
 */
public final class HeaderApplyCapability implements PlannableCapability<EffectiveHeaderPolicy> {
    public static final CapabilityId ID = BuiltInCapabilityCatalog.HEADER_APPLY;

    @Override
    public CapabilityId id() { return ID; }

    @Override
    public Set<CapabilitySlot> slots() { return Set.of(CapabilitySlot.FORWARD); }

    @Override
    public CapabilityRepeat repeat() { return CapabilityRepeat.ONCE_PER_REQUEST; }

    @Override
    public List<Unit> units(EffectiveHeaderPolicy policy, CapabilityContext context) {
        return List.of(new Unit(policy));
    }

    public record Unit(EffectiveHeaderPolicy policy) implements CapabilityUnit {
        public Unit { Require.notNull(policy); }

        @Override
        public String unitId() { return ID.value(); }
    }

    /** Handing the plan forward is the whole result; the transport does the mutating. */
    public record Outcome(EffectiveHeaderPolicy policy) implements CapabilityOutcome {
        public Outcome { Require.notNull(policy); }

        @Override
        public Disposition disposition() { return Disposition.CONTINUE; }

    }
}
