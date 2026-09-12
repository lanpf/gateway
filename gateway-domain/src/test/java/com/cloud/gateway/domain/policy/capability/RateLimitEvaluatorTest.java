package com.cloud.gateway.domain.policy.capability;

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
        RateLimitPolicy rule = rule("ip", RateLimitDimension.IP, 1);
        ApplicableQuota open = new ApplicableQuota(rule, "ip", RedisFailurePolicy.OPEN_WITH_LOCAL);
        ApplicableQuota closed = new ApplicableQuota(rule, "ip", RedisFailurePolicy.CLOSED);
        assertTrue(open.shouldFallback(RateLimitDecision.unavailable(rule.dimension(), "ip")));
        assertFalse(closed.shouldFallback(RateLimitDecision.unavailable(rule.dimension(), "ip")));
        assertFalse(open.shouldFallback(RateLimitDecision.allowed(rule.dimension(), "ip", 0)));
        assertFalse(open.shouldFallback(RateLimitDecision.rejected(rule.dimension(), "ip", Duration.ofSeconds(1))));
    }

    @Test
    void shouldSelectEveryApplicableRuleAndDeduplicateAcrossGroupsWithoutMutatingApplied() {
        RateLimitRules group = new RateLimitRules(List.of(rule("ip", RateLimitDimension.IP, 1),
                rule("client", RateLimitDimension.CLIENT, 2)), RedisFailurePolicy.OPEN_WITH_LOCAL);
        java.util.Set<String> applied = new java.util.HashSet<>();
        List<ApplicableQuota> selected = RateLimitEvaluator.applicable(List.of(group, group),
                Map.of(RateLimitDimension.IP, "192.0.2.1", RateLimitDimension.CLIENT, " raw-client "), false, applied);
        assertEquals(List.of("ip", "client"), selected.stream().map(value -> value.rule().ruleId()).toList());
        assertEquals(" raw-client ", selected.get(1).key());
        assertEquals(RedisFailurePolicy.OPEN_WITH_LOCAL, selected.get(1).failurePolicy());
        assertTrue(applied.isEmpty());
        assertThrows(UnsupportedOperationException.class, selected::clear);
        applied.add("ip");
        assertTrue(RateLimitEvaluator.applicable(List.of(group),
                Map.of(RateLimitDimension.IP, "192.0.2.1", RateLimitDimension.CLIENT, "client"), true, applied).isEmpty());
    }

    @Test
    void shouldTreatMissingNullEmptyAndWhitespaceDimensionsIdentically() {
        RateLimitRules group = new RateLimitRules(List.of(rule("client", RateLimitDimension.CLIENT, 1)), null);
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
                () -> new RateLimitPolicy(" ", RateLimitDimension.IP, 1, 1, RateLimitMode.EXACT));
        assertThrows(DomainException.class,
                () -> new RateLimitPolicy("x", null, 1, 1, RateLimitMode.EXACT));
        assertThrows(DomainException.class,
                () -> new RateLimitPolicy("x", RateLimitDimension.IP, 0, 1, RateLimitMode.EXACT));
        assertThrows(DomainException.class,
                () -> new RateLimitPolicy("x", RateLimitDimension.IP, 1, 0, RateLimitMode.EXACT));
        assertThrows(DomainException.class,
                () -> new RateLimitPolicy("x", RateLimitDimension.IP, 1, 1, null));
        assertThrows(DomainException.class, () -> new RateLimitRules(List.of(
                rule("same", RateLimitDimension.APP, 1), rule("same", RateLimitDimension.IP, 2)), null));
        assertThrows(IllegalArgumentException.class, () -> RateLimitMode.valueOf("PREALLOCATED"));
        assertEquals(RedisFailurePolicy.CLOSED, new RateLimitRules(null, null).onRedisFailure());
    }

    @Test
    void shouldDefensivelyCopyRules() {
        ArrayList<RateLimitPolicy> source = new ArrayList<>();
        source.add(rule("one", RateLimitDimension.IP, 1));
        RateLimitRules rules = new RateLimitRules(source, null);
        source.clear();
        assertEquals(1, rules.rules().size());
        assertThrows(UnsupportedOperationException.class, () -> rules.rules().clear());
    }

    private static RateLimitPolicy rule(String id, RateLimitDimension dimension, int capacity) {
        return new RateLimitPolicy(id, dimension, capacity, 1, RateLimitMode.EXACT);
    }
}
