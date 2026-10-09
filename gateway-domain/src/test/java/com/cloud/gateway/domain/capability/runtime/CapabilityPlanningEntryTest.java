package com.cloud.gateway.domain.capability.runtime;

import com.cloud.gateway.domain.capability.resilience.EffectiveTimeoutPolicy;

import com.cloud.framework.core.error.FrameworkException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityPlanningEntryTest {
    @Test
    void shouldRequirePolicyAndRepresentUnconfiguredTimeoutExplicitly() {
        assertThrows(FrameworkException.class,
                () -> new CapabilityPlanningEntry<>(null, null));
        assertThrows(FrameworkException.class, () -> new CapabilityPlanningEntry<>(new TimeoutCapability(), null));
        assertTrue(new CapabilityPlanningEntry<>(new TimeoutCapability(),
                EffectiveTimeoutPolicy.unconfigured()).units(null).isEmpty());
    }

    @Test
    void shouldRejectMissingHeaderPolicyRatherThanSkipSecurityPreparation() {
        assertThrows(FrameworkException.class, () -> new HeaderApplyCapability().units(null, null));
    }
}
