package com.cloud.gateway.domain.capability.runtime;

import java.util.Set;

/** Strongly typed payload implemented by each capability. */
public interface CapabilityUnit {
    /** Non-blank, capability-local identity; equality must cover the payload and required facts. */
    String unitId();
    /** Non-null immutable set without null elements. */
    default Set<CapabilityFact> requiredFacts() {
        return Set.of();
    }
}
