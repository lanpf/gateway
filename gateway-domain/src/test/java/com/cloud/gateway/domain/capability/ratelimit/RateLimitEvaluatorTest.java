package com.cloud.gateway.domain.capability.ratelimit;

import com.cloud.gateway.domain.common.DomainException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RateLimitEvaluatorTest {
    @Test
    void shouldPermitLocalFallbackOnlyForUnavailableOpenQuota() {
        RateLimitRule rule = rule("ip", RateLimitDimension.IP, 1);
        ApplicableQuota open = new ApplicableQuota(rule, "ip", RedisFailureMode.OPEN_WITH_LOCAL);
        ApplicableQuota closed = new ApplicableQuota(rule, "ip", RedisFailureMode.CLOSED);
        assertTrue(open.shouldFallback(RateLimitDecision.unavailable(rule.dimension(), "ip")));
        assertFalse(closed.shouldFallback(RateLimitDecision.unavailable(rule.dimension(), "ip")));
        assertFalse(open.shouldFallback(RateLimitDecision.allowed(rule.dimension(), "ip", 0)));
        assertFalse(open.shouldFallback(RateLimitDecision.rejected(rule.dimension(), "ip", Duration.ofSeconds(1))));
    }

    @Test
    void shouldSelectEveryApplicableRuleAndDeduplicateAcrossGroupsWithoutMutatingApplied() {
        RateLimitConfig group = new RateLimitConfig(List.of(rule("ip", RateLimitDimension.IP, 1),
                rule("client", RateLimitDimension.CLIENT, 2)), RedisFailureMode.OPEN_WITH_LOCAL);
        java.util.Set<RateLimitRuleId> applied = new java.util.HashSet<>();
        List<ApplicableQuota> selected = RateLimitEvaluator.applicable(List.of(group, group),
                Map.of(RateLimitDimension.IP, "192.0.2.1", RateLimitDimension.CLIENT, " raw-client "), false, applied);
        assertEquals(List.of("ip", "client"), selected.stream().map(value -> value.rule().ruleId().value()).toList());
        assertEquals(" raw-client ", selected.get(1).key());
        assertEquals(RedisFailureMode.OPEN_WITH_LOCAL, selected.get(1).failureMode());
        assertTrue(applied.isEmpty());
        assertThrows(UnsupportedOperationException.class, selected::clear);
        applied.add(new RateLimitRuleId("ip"));
        assertTrue(RateLimitEvaluator.applicable(List.of(group),
                Map.of(RateLimitDimension.IP, "192.0.2.1", RateLimitDimension.CLIENT, "client"), true, applied).isEmpty());
    }

    @Test
    void shouldTreatMissingNullEmptyAndWhitespaceDimensionsIdentically() {
        RateLimitConfig group = new RateLimitConfig(List.of(rule("client", RateLimitDimension.CLIENT, 1)), null);
        java.util.Map<RateLimitDimension, String> dimensions = new java.util.EnumMap<>(RateLimitDimension.class);
        assertTrue(RateLimitEvaluator.applicable(List.of(group), dimensions, false, java.util.Set.of()).isEmpty());
        for (String value : java.util.Arrays.asList(null, "", " ", "\t", "\u2003")) {
            dimensions.put(RateLimitDimension.CLIENT, value);
            assertTrue(RateLimitEvaluator.applicable(List.of(group), dimensions, false, java.util.Set.of()).isEmpty());
        }
    }

    @Test
    void shouldAggregateMaximumRetryAndPreferUnavailableInEitherOrder() {
        RateLimitDecision shortWait = RateLimitDecision.rejected(RateLimitDimension.IP, "ip", Duration.ofMillis(100));
        RateLimitDecision longWait = RateLimitDecision.rejected(RateLimitDimension.CLIENT, "client", Duration.ofMillis(900));
        RateLimitDecision unavailable = RateLimitDecision.unavailable(RateLimitDimension.SUBJECT, "subject");
        assertSame(longWait, RateLimitEvaluator.aggregate(List.of(shortWait, longWait)));
        assertSame(longWait, RateLimitEvaluator.aggregate(List.of(longWait, shortWait)));
        assertSame(unavailable, RateLimitEvaluator.aggregate(List.of(unavailable, longWait)));
        assertSame(unavailable, RateLimitEvaluator.aggregate(List.of(longWait, unavailable)));
    }

    @Test
    void shouldReturnTightestRemainingWhenAllAllowedAndUnlimitedForEmptyInput() {
        RateLimitDecision tighter = RateLimitDecision.allowed(RateLimitDimension.APP, "app", 1);
        assertSame(tighter, RateLimitEvaluator.aggregate(List.of(
                RateLimitDecision.allowed(RateLimitDimension.CLIENT, "client", 2), tighter)));
        RateLimitDecision empty = RateLimitEvaluator.aggregate(List.of());
        assertTrue(empty.allowed());
        assertEquals(Long.MAX_VALUE, empty.remaining());
    }

    @Test
    void shouldRejectInvalidPoliciesAndDuplicateStableRuleIds() {
        assertThrows(DomainException.class,
                () -> new RateLimitRule(" ", RateLimitDimension.IP, 1, 1, RateLimitMode.EXACT));
        assertThrows(DomainException.class,
                () -> new RateLimitRule("x", null, 1, 1, RateLimitMode.EXACT));
        assertThrows(DomainException.class,
                () -> new RateLimitRule("x", RateLimitDimension.IP, 0, 1, RateLimitMode.EXACT));
        assertThrows(DomainException.class,
                () -> new RateLimitRule("x", RateLimitDimension.IP, 1, 0, RateLimitMode.EXACT));
        assertThrows(DomainException.class,
                () -> new RateLimitRule("x", RateLimitDimension.IP, 1, 1, null));
        assertThrows(DomainException.class, () -> new RateLimitConfig(List.of(
                rule("same", RateLimitDimension.APP, 1), rule("same", RateLimitDimension.IP, 2)), null));
        assertThrows(IllegalArgumentException.class, () -> RateLimitMode.valueOf("PREALLOCATED"));
        assertEquals(RedisFailureMode.CLOSED, new RateLimitConfig(null, null).failureMode());
    }

    @Test
    void shouldDefensivelyCopyRules() {
        ArrayList<RateLimitRule> source = new ArrayList<>();
        source.add(rule("one", RateLimitDimension.IP, 1));
        RateLimitConfig rules = new RateLimitConfig(source, null);
        source.clear();
        assertEquals(1, rules.rules().size());
        assertThrows(UnsupportedOperationException.class, () -> rules.rules().clear());
    }

    private static RateLimitRule rule(String id, RateLimitDimension dimension, int capacity) {
        return new RateLimitRule(id, dimension, capacity, 1, RateLimitMode.EXACT);
    }
}
