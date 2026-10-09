package com.cloud.gateway.domain.capability.runtime;


import com.cloud.framework.core.util.NullSafe;

import java.util.List;
import java.util.Set;

/**
 * Cumulative keys and current-slot outcomes; skipped units are never recorded.
 *
 * <p>{@code executed} is a <strong>de-duplication ledger</strong>: it answers "which units have already
 * run for this request", so that a rule running in two slots does not run twice. It is deliberately
 * <em>not</em> a claim that effects happened. Concrete outcomes distinguish decisions from plans:
 * a timeout budget or header plan must still be applied by its adapter after it is produced.
 */
public record CapabilityExecution(Set<CapabilityExecutionKey> executed, List<CapabilityOutcome> outcomes) {
    public CapabilityExecution {
        executed = NullSafe.copyOf(executed);
        outcomes = NullSafe.copyOf(outcomes);
    }

    public static CapabilityExecution empty() {
        return new CapabilityExecution(Set.of(), List.of());
    }

    /** Whether the units still to run for this request may proceed. */
    public boolean continues() {
        return outcomes.stream()
                .allMatch(outcome -> outcome.disposition() == CapabilityOutcome.Disposition.CONTINUE);
    }
}
