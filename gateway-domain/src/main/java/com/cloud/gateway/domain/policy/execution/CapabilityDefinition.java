package com.cloud.gateway.domain.policy.execution;

import com.cloud.gateway.domain.policy.bundle.ResolvedCapabilities;
import java.util.List;
import java.util.Set;

/** Pure capability-specific eligibility; no execution or external access. */
public interface CapabilityDefinition {
    String capability();
    Set<CapabilitySlot> slots();
    CapabilityRepeat repeat();
    default Set<CapabilityFact> requiredFacts() {
        return Set.of(CapabilityFact.REQUEST);
    }
    List<? extends CapabilityUnit> units(ResolvedCapabilities resolved, CapabilityContext context);
}
