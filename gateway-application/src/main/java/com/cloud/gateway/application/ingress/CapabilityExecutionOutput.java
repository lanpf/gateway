package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.capability.configuration.EffectiveCapabilityPolicies;
import com.cloud.gateway.domain.capability.runtime.CapabilityExecution;
import java.util.Objects;

/** Resolved nodes and the execution report belong to the same invocation, including on denial. */
public record CapabilityExecutionOutput(EffectiveCapabilityPolicies capabilities, CapabilityExecution execution) {
    public CapabilityExecutionOutput {
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(execution, "execution");
    }
}
