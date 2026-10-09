package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.capability.resilience.EffectiveTimeoutPolicy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloud.gateway.domain.capability.runtime.TimeoutCapability;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapabilityRegistrationTest {
    @Test
    void shouldRequireDefinitionAndConfigurationSource() {
        assertThrows(com.cloud.framework.core.error.FrameworkException.class,
                () -> new CapabilityRegistration<>(null, config -> config.get(EffectiveTimeoutPolicy.class)));
        assertThrows(com.cloud.framework.core.error.FrameworkException.class,
                () -> new CapabilityRegistration<>(new TimeoutCapability(), null));
    }

    @Test
    void shouldRejectDuplicateCapabilityDefinitionsAtStartup() {
        CapabilityRegistration<?> registration = planner().get(0);
        assertThrows(IllegalArgumentException.class, () -> CapabilityRegistration.validate(
                List.of(registration, registration), List.of(new TimeoutCapabilityExecutor())));
    }

    private static List<CapabilityRegistration<?>> planner() {
        return List.of(
                new CapabilityRegistration<>(new TimeoutCapability(), config -> config.get(EffectiveTimeoutPolicy.class)));
    }

    @Test
    void shouldAcceptMatchingPlannerAndExecutor() {
        assertDoesNotThrow(() -> CapabilityRegistration.validate(planner(), List.of(new TimeoutCapabilityExecutor())));
    }

    @Test
    void shouldRejectMissingExecutor() {
        assertThrows(IllegalArgumentException.class, () -> CapabilityRegistration.validate(planner(), List.of()));
    }

    @Test
    void shouldRejectMissingPlanningEntry() {
        assertThrows(IllegalArgumentException.class, () -> CapabilityRegistration.validate(
                List.of(), List.of(new TimeoutCapabilityExecutor())));
    }

    @Test
    void shouldRejectDuplicateExecutor() {
        assertThrows(IllegalArgumentException.class, () -> CapabilityRegistration.validate(
                planner(), List.of(new TimeoutCapabilityExecutor(), new TimeoutCapabilityExecutor())));
    }
}
