package com.cloud.gateway.domain.policy.execution;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import java.util.List;
import java.util.Set;

/** Cumulative keys and current-slot outcomes; skipped units are never recorded. */
public record CapabilityExecution(Set<CapabilityExecutionKey> executed, List<CapabilityOutcome> outcomes) {
    public CapabilityExecution {
        executed = Set.copyOf(Require.notNull(executed, DomainException::missingField));
        outcomes = List.copyOf(Require.notNull(outcomes, DomainException::missingField));
    }

    public static CapabilityExecution empty() {
        return new CapabilityExecution(Set.of(), List.of());
    }

    public boolean allowed() {
        return outcomes.stream().allMatch(CapabilityOutcome::allowed);
    }
}
