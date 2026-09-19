package com.cloud.gateway.domain.policy.execution;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.policy.bundle.ResolvedCapabilities;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Registration order is code-owned. Selection and cross-slot de-duplication are domain rules. */
public final class CapabilityPlanner {
    private final List<CapabilityDefinition> definitions;

    public CapabilityPlanner(List<CapabilityDefinition> definitions) {
        this.definitions = List.copyOf(Require.notNull(definitions, DomainException::missingField));
        Set<String> registered = new HashSet<>();
        for (CapabilityDefinition definition : this.definitions) {
            Require.notBlank(definition.capability(), DomainException::missingField);
            Require.notEmpty(definition.slots(), DomainException::missingField);
            Require.notNull(definition.repeat(), DomainException::missingField);
            if (!registered.add(definition.capability())) {
                throw DomainException.invalidField();
            }
        }
    }

    public CapabilityPlan plan(ResolvedCapabilities resolved, CapabilityContext context,
            Set<CapabilityExecutionKey> executed) {
        Require.notNull(resolved, DomainException::missingField);
        Require.notNull(context, DomainException::missingField);
        Require.notNull(executed, DomainException::missingField);
        Set<CapabilityExecutionKey> selected = new HashSet<>(executed);
        List<CapabilityPlan.Step> steps = new ArrayList<>();
        for (CapabilityDefinition definition : definitions) {
            if (!definition.slots().contains(context.slot())
                    || !context.facts().containsAll(definition.requiredFacts())) {
                continue;
            }
            List<CapabilityUnit> units = new ArrayList<>();
            List<CapabilityExecutionKey> keys = new ArrayList<>();
            for (CapabilityUnit unit : definition.units(resolved, context)) {
                if (!context.facts().containsAll(unit.requiredFacts())) {
                    continue;
                }
                CapabilityExecutionKey key = new CapabilityExecutionKey(definition.capability(), unit.unitId(),
                        definition.repeat() == CapabilityRepeat.ONCE_PER_SLOT ? context.slot() : null);
                if (selected.add(key)) {
                    units.add(unit);
                    keys.add(key);
                }
            }
            if (!units.isEmpty()) {
                steps.add(new CapabilityPlan.Step(definition.capability(), units, keys));
            }
        }
        return new CapabilityPlan(steps);
    }
}
