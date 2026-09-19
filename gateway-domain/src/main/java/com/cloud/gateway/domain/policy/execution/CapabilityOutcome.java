package com.cloud.gateway.domain.policy.execution;

/** Typed capability result. Its HTTP projection belongs to interfaces. */
public interface CapabilityOutcome {
    boolean allowed();
}
