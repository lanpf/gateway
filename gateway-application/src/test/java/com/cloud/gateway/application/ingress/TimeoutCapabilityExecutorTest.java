package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.capability.resilience.EffectiveTimeoutPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.cloud.gateway.domain.capability.runtime.CapabilityOutcome;
import com.cloud.gateway.domain.capability.runtime.TimeoutCapability;
import com.cloud.gateway.domain.capability.resilience.TimeoutConfig;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeoutCapabilityExecutorTest {

    @Test
    void shouldHandTheDeclaredBudgetForwardWithoutDeciding() {
        TimeoutConfig policy = new TimeoutConfig(
                Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(5));
        TimeoutCapability.Outcome outcome = new TimeoutCapabilityExecutor()
                .execute(List.of(new TimeoutCapability.Unit(new EffectiveTimeoutPolicy(policy))))
                .toCompletableFuture().join();
        assertSame(policy, outcome.policy().config());
        assertEquals(CapabilityOutcome.Disposition.CONTINUE, outcome.disposition());
    }
}
