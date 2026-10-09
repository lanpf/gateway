package com.cloud.gateway.domain.capability.runtime;

import com.cloud.gateway.domain.capability.resilience.EffectiveTimeoutPolicy;

import com.cloud.gateway.domain.capability.header.EffectiveHeaderPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.cloud.gateway.domain.capability.runtime.CapabilityOutcome.Disposition;
import com.cloud.gateway.domain.capability.header.HeaderConfig;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.resilience.TimeoutConfig;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Flow control does not replace the concrete outcome's business payload. */
class CapabilityOutcomeTest {
    private static final TimeoutConfig TIMEOUT =
            new TimeoutConfig(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3));

    @Test
    void shouldContinueWithAllowedQuotaDecision() {
        RateLimitDecision decision = RateLimitDecision.allowed(null, null, 1);
        RateLimitCapability.Outcome outcome = new RateLimitCapability.Outcome(decision);
        assertEquals(Disposition.CONTINUE, outcome.disposition());
        assertSame(decision, outcome.decision());
    }

    @Test
    void shouldKeepTimeoutBudgetForTheAdapterWhileContinuing() {
        TimeoutCapability.Outcome outcome = new TimeoutCapability.Outcome(new EffectiveTimeoutPolicy(TIMEOUT));
        assertEquals(Disposition.CONTINUE, outcome.disposition());
        assertSame(TIMEOUT, outcome.policy().config());
    }

    @Test
    void shouldKeepHeaderPlanForTheAdapterWhileContinuing() {
        HeaderConfig policy = HeaderConfig.defaults(null);
        HeaderApplyCapability.Outcome outcome = new HeaderApplyCapability.Outcome(new EffectiveHeaderPolicy(policy));
        assertEquals(Disposition.CONTINUE, outcome.disposition());
        assertSame(policy, outcome.policy().config());
    }

    @Test
    void shouldStopWithoutLosingQuotaRejectionOrUnavailability() {
        for (RateLimitDecision decision : List.of(
                RateLimitDecision.rejected(null, null, Duration.ofSeconds(3)),
                RateLimitDecision.unavailable(null, null))) {
            RateLimitCapability.Outcome outcome = new RateLimitCapability.Outcome(decision);
            assertEquals(Disposition.STOP, outcome.disposition());
            assertSame(decision, outcome.decision());
            assertFalse(new CapabilityExecution(Set.of(), List.of(outcome)).continues());
        }
    }
}
