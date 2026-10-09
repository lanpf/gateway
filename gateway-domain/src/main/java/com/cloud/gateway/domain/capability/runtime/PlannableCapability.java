package com.cloud.gateway.domain.capability.runtime;

import com.cloud.gateway.domain.capability.CapabilityId;

import java.util.List;
import java.util.Set;

/**
 * The one contract every capability is planned through: it declares when it may run ({@link #slots}),
 * what it needs to know ({@link #requiredFacts}), how often it may run ({@link #repeat}) and which
 * units it contributes ({@link #units}). {@code CapabilityPlanner} accepts nothing else.
 *
 * <p>Configuration count, unit count and supported slot count are independent dimensions; all
 * policyConfig use this contract. An unconfigured capability is represented by its effective policy,
 * not a null planning input.
 *
 * <p>{@code P} is the capability's own resolved policy, so a capability never sees the nodes belonging
 * to other policyConfig.
 */
public interface PlannableCapability<P> {
    CapabilityId id();

    /** Fixed, non-empty immutable set without null elements. */
    Set<CapabilitySlot> slots();
    CapabilityRepeat repeat();
    /** Non-null immutable set without null elements; all listed facts must be available. */
    default Set<CapabilityFact> requiredFacts() {
        return Set.of();
    }
    /**
     * Non-null immutable list without null elements. Unit identities are capability-local and stable
     * across slots for the same request. Equal repeated units fold; differing payloads sharing an
     * identity are rejected, even when that identity has already executed or its facts are absent.
     */
    List<? extends CapabilityUnit> units(P policy, CapabilityContext context);
}
