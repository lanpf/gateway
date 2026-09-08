package com.cloud.gateway.domain.policy.capability;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitEvaluatorTest {
    private final RateLimitQuota quota = (rule, key) -> rule.capacity() >= 1000
            ? RateLimitDecision.allowed(rule.dimension(), key, rule.capacity() / 2)
            : RateLimitDecision.rejected(rule.dimension(), key, Duration.ofSeconds(3));

    @Test
    void shouldRejectWhenAnyDimensionRejectsAndReturnLongestRetryAfter() {
        var decision = RateLimitEvaluator.evaluate(
                List.of(
                        new RateLimitPolicy(RateLimitDimension.SUBJECT, 2000, 100, RateLimitMode.EXACT, null),
                        new RateLimitPolicy(RateLimitDimension.IP, 500, 50, RateLimitMode.EXACT, null)),
                quota,
                Map.of(RateLimitDimension.SUBJECT, "u-1", RateLimitDimension.IP, "1.2.3.4"));

        assertFalse(decision.allowed());
        assertEquals(RateLimitDimension.IP, decision.dimension());
        assertEquals(Duration.ofSeconds(3), decision.retryAfter());
    }

    @Test
    void shouldReturnTightestRemainingWhenAllAllowed() {
        var decision = RateLimitEvaluator.evaluate(
                List.of(
                        new RateLimitPolicy(RateLimitDimension.SUBJECT, 2000, 100, RateLimitMode.EXACT, null),
                        new RateLimitPolicy(RateLimitDimension.APP, 1500, 100, RateLimitMode.EXACT, null)),
                quota,
                Map.of(RateLimitDimension.SUBJECT, "u-1", RateLimitDimension.APP, "app-1"));

        assertTrue(decision.allowed());
        assertEquals(RateLimitDimension.APP, decision.dimension());
        assertEquals(750, decision.remaining());
    }

    @Test
    void shouldSkipRulesWithMissingDimensionValue() {
        var decision = RateLimitEvaluator.evaluate(
                List.of(new RateLimitPolicy(RateLimitDimension.SUBJECT, 2000, 100, RateLimitMode.EXACT, null)),
                quota,
                Map.of());

        assertTrue(decision.allowed());
        assertEquals(Long.MAX_VALUE, decision.remaining());
    }

    @Test
    void shouldEvaluateEachDimensionOnlyOnce() {
        var decision = RateLimitEvaluator.evaluate(
                List.of(
                        new RateLimitPolicy(RateLimitDimension.SUBJECT, 2000, 100, RateLimitMode.EXACT, null),
                        new RateLimitPolicy(RateLimitDimension.SUBJECT, 3000, 100, RateLimitMode.EXACT, null)),
                quota,
                Map.of(RateLimitDimension.SUBJECT, "u-1"));

        assertEquals(1000, decision.remaining());
    }
}
