package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.capability.configuration.EffectiveCapabilityConfig;
import com.cloud.gateway.domain.capability.runtime.CapabilityExecution;
import java.util.Objects;

/** Resolved nodes and the execution report belong to the same invocation, including on denial. */
public record CapabilityExecutionOutput(EffectiveCapabilityConfig capabilities, CapabilityExecution execution) {
    public CapabilityExecutionOutput {
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(execution, "execution");
    }
}
