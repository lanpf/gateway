package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.policy.bundle.ResolvedCapabilities;
import com.cloud.gateway.domain.policy.execution.CapabilityExecution;
import java.util.Objects;

/** Resolved nodes and the execution report belong to the same invocation, including on denial. */
public record CapabilityExecutionOutput(ResolvedCapabilities capabilities, CapabilityExecution execution) {
    public CapabilityExecutionOutput {
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(execution, "execution");
    }
}
