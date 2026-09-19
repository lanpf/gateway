package com.cloud.gateway.domain.policy.execution;

import java.util.Set;

/** Strongly typed payload implemented by each capability. */
public interface CapabilityUnit {
    String unitId();
    default Set<CapabilityFact> requiredFacts() {
        return Set.of();
    }
}
