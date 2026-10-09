package com.cloud.gateway.application.ingress;

import com.cloud.framework.core.validation.Require;
import com.cloud.framework.core.util.NullSafe;
import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.policy.EffectiveCapabilityPolicies;
import com.cloud.gateway.domain.capability.runtime.CapabilityPlanningEntry;
import com.cloud.gateway.domain.capability.runtime.PlannableCapability;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.apache.commons.lang3.Validate;

/** Shared, code-owned registration; binds only the policy explicitly supplied by this request. */
public record CapabilityRegistration<P>(PlannableCapability<P> capability,
        Function<EffectiveCapabilityPolicies, P> policySource) {
    public CapabilityRegistration {
        Require.allNotNull(capability, policySource);
        Objects.requireNonNull(capability.id(), "capability id must not be null");
        Validate.notEmpty(capability.slots(), "capability slots must not be empty");
        Objects.requireNonNull(capability.repeat(), "capability repeat");
        Objects.requireNonNull(capability.requiredFacts(), "capability required facts");
        NullSafe.forEach(capability.slots());
        NullSafe.forEach(capability.requiredFacts());
    }

    public CapabilityPlanningEntry<P> bind(EffectiveCapabilityPolicies config) {
        Require.notNull(config);
        return new CapabilityPlanningEntry<>(capability, policySource.apply(config));
    }

    /** Every planning entry has a typed source; both sides must cover exactly the same capability IDs. */
    public static void validate(List<CapabilityRegistration<?>> registrations, List<CapabilityExecutor<?>> executors) {
        Require.allNotNull(registrations, executors);
        NullSafe.forEach(registrations);
        NullSafe.forEach(executors);
        Set<CapabilityId> planned = new LinkedHashSet<>();
        for (CapabilityRegistration<?> registration : registrations) {
            CapabilityId id = registration.capability().id();
            Validate.isTrue(planned.add(id), "duplicate capability registration: %s", id);
        }
        Set<CapabilityId> registered = new LinkedHashSet<>();
        for (CapabilityExecutor<?> executor : executors) {
            CapabilityId id = executor.capability();
            Validate.isTrue(registered.add(id), "duplicate capability executor: %s", id);
        }
        Set<CapabilityId> gaps = new LinkedHashSet<>();
        for (CapabilityId capability : planned) {
            if (!registered.contains(capability)) {
                gaps.add(capability);
            }
        }
        for (CapabilityId capability : registered) {
            if (!planned.contains(capability)) {
                gaps.add(capability);
            }
        }
        Validate.isTrue(gaps.isEmpty(), "incomplete capability registration: %s", gaps);
    }
}
