package com.cloud.gateway.domain.capability.runtime;

import com.cloud.framework.core.error.FrameworkException;
import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Registration order is code-owned. Selection and cross-slot de-duplication are domain rules. */
public final class CapabilityPlanner {
    /** Validates the complete request input before generating any units. */
    private List<? extends CapabilityPlanningEntry<?>> validateEntries(List<? extends CapabilityPlanningEntry<?>> entries) {
        List<? extends CapabilityPlanningEntry<?>> result = NullSafe.copyOf(entries, entry -> {
            PlannableCapability<?> capability = entry.capability();
            if (capability == null || capability.id() == null || capability.repeat() == null) {
                throw FrameworkException.invalidObject();
            }
            if (capability.slots() == null || capability.requiredFacts() == null) {
                throw FrameworkException.invalidObject();
            }
            NullSafe.forEach(capability.slots());
            NullSafe.forEach(capability.requiredFacts());
        });
        Require.noDuplicates(result, entry -> entry.capability().id());
        return result;
    }


    /** Repeated equivalent units fold; conflicting payloads under one identity are invalid. */
    private List<? extends CapabilityUnit> validateUnits(List<? extends CapabilityUnit> units) {
        if (units == null) { throw FrameworkException.invalidObject(); }
        List<? extends CapabilityUnit> result = NullSafe.copyOf(units, unit -> {
            if (unit.unitId() == null || unit.unitId().isBlank()) {
                throw FrameworkException.invalidObject();
            }
            if (unit.requiredFacts() == null) { throw FrameworkException.invalidObject(); }
            NullSafe.forEach(unit.requiredFacts());
        });
        List<? extends CapabilityUnit> distinct = result.stream().distinct().toList();
        Require.noDuplicates(distinct, CapabilityUnit::unitId);
        return distinct;
    }

    /** The application supplies request-local entries in its fixed registration order. */
    public CapabilityPlan plan(
            List<? extends CapabilityPlanningEntry<?>> entries,
            CapabilityContext context,
            Set<CapabilityExecutionKey> executed) {
        Require.notNull(context);
        Set<CapabilityExecutionKey> selected = new HashSet<>(NullSafe.copyOf(executed));

        List<CapabilityPlan.Step> steps = new ArrayList<>();
        validateEntries(entries).forEach(entry -> {
            PlannableCapability<?> definition = entry.capability();
            if (!definition.slots().contains(context.slot())
                    || !context.facts().containsAll(definition.requiredFacts())) {
                return;
            }
            List<CapabilityUnit> units = new ArrayList<>();
            List<CapabilityExecutionKey> keys = new ArrayList<>();
            validateUnits(entry.units(context)).forEach(unit -> {
                if (!context.facts().containsAll(unit.requiredFacts())) {
                    return;
                }
                CapabilityExecutionKey key = new CapabilityExecutionKey(definition.id(), unit.unitId(),
                        definition.repeat() == CapabilityRepeat.ONCE_PER_SLOT ? context.slot() : null);
                if (selected.add(key)) {
                    units.add(unit);
                    keys.add(key);
                }
            });
            if (!units.isEmpty()) {
                steps.add(new CapabilityPlan.Step(definition.id(), units, keys));
            }
        });
        return new CapabilityPlan(steps);
    }

}
