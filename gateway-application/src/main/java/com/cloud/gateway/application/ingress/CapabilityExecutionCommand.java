package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.policy.bundle.CapabilityComposition;
import com.cloud.gateway.domain.policy.execution.CapabilityContext;
import com.cloud.gateway.domain.policy.execution.CapabilityExecutionKey;
import java.util.Objects;
import java.util.Set;

/** One slot's facts from the captured snapshot, plus the request's prior execution keys. */
public record CapabilityExecutionCommand(CapabilityComposition composition, CapabilityContext context,
                                         Set<CapabilityExecutionKey> executed) {
    public CapabilityExecutionCommand {
        Objects.requireNonNull(composition, "composition");
        Objects.requireNonNull(context, "context");
        executed = Set.copyOf(Objects.requireNonNull(executed, "executed"));
    }
}
