package com.cloud.gateway.application.quota;

import static org.junit.jupiter.api.Assertions.*;

import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDimension;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitMode;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitConfig;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitQuota;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRuleId;
import com.cloud.gateway.domain.capability.ratelimit.RedisFailureMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QuotaCommandServiceTest {
    @Test
    void shouldExecuteAllApplicableRulesAfterRejectionAndReturnMaximumRetry() {
        List<String> calls = new ArrayList<>();
        List<RateLimitDecision> observations = new ArrayList<>();
        QuotaCommandService service = service((rule, key) -> {
            calls.add(rule.ruleId().value());
            return RateLimitDecision.rejected(rule.dimension(), key, Duration.ofMillis(rule.capacity()));
        }, unusedLocal(), (rule, decision) -> observations.add(decision));
        Set<RateLimitRuleId> applied = new HashSet<>();
        RateLimitDecision decision = service.acquire(command(RedisFailureMode.CLOSED), applied);
        assertEquals(List.of("short", "long", "ip"), calls);
        assertEquals(Set.of(new RateLimitRuleId("short"), new RateLimitRuleId("long"), new RateLimitRuleId("ip")), applied);
        assertEquals(3, observations.size());
        assertEquals(Duration.ofMillis(900), decision.retryAfter());
    }

    @Test
    void shouldCoordinateFallbackOnlyForUnavailableOpenRulesAndObserveFinalOutcomeOnce() {
        List<String> localCalls = new ArrayList<>();
        List<RateLimitDecision> observations = new ArrayList<>();
        QuotaCommandService service = service((rule, key) -> RateLimitDecision.unavailable(rule.dimension(), key),
                (rule, key) -> {
                    localCalls.add(rule.ruleId().value());
                    return RateLimitDecision.allowed(rule.dimension(), key, 2);
                }, (rule, decision) -> observations.add(decision));
        QuotaCommand open = command(RedisFailureMode.OPEN_WITH_LOCAL);
        assertTrue(service.acquire(open, new HashSet<>()).allowed());
        assertEquals(3, localCalls.size());
        assertEquals(3, observations.size());
        assertTrue(observations.stream().allMatch(RateLimitDecision::allowed));
        assertTrue(service.acquire(command(RedisFailureMode.CLOSED), new HashSet<>()).unavailable());
        assertEquals(3, localCalls.size());
    }

    @Test
    void shouldNeverFallbackOnRedisDenialOrSuccess() {
        AtomicInteger calls = new AtomicInteger();
        QuotaCommandService service = service((rule, key) -> calls.incrementAndGet() == 1
                ? RateLimitDecision.rejected(rule.dimension(), key, Duration.ofSeconds(1))
                : RateLimitDecision.allowed(rule.dimension(), key, 1), unusedLocal(), QuotaObserver.noop());
        assertFalse(service.acquire(command(RedisFailureMode.OPEN_WITH_LOCAL), new HashSet<>()).allowed());
        assertEquals(3, calls.get());
    }

    @Test
    void shouldKeepUnavailableDominantWhenLocalCapacityIsUnavailableAndStillExecuteLaterRules() {
        AtomicInteger calls = new AtomicInteger();
        QuotaCommandService service = service((rule, key) -> RateLimitDecision.unavailable(rule.dimension(), key),
                (rule, key) -> calls.incrementAndGet() == 1
                        ? RateLimitDecision.unavailable(rule.dimension(), key)
                        : RateLimitDecision.rejected(rule.dimension(), key, Duration.ofSeconds(10)), QuotaObserver.noop());
        assertTrue(service.acquire(command(RedisFailureMode.OPEN_WITH_LOCAL), new HashSet<>()).unavailable());
        assertEquals(3, calls.get());
    }

    @Test
    void shouldShareExecutionRecordAcrossStagesAndDeduplicateRepeatedGroups() {
        List<String> calls = new ArrayList<>();
        QuotaCommandService service = service((rule, key) -> {
            calls.add(rule.ruleId().value());
            return RateLimitDecision.allowed(rule.dimension(), key, 1);
        }, unusedLocal(), QuotaObserver.noop());
        QuotaCommand all = command(RedisFailureMode.CLOSED);
        Set<RateLimitRuleId> applied = new HashSet<>();
        service.acquire(new QuotaCommand(all.groups(), all.dimensions(), true), applied);
        assertEquals(List.of("ip"), calls);
        service.acquire(new QuotaCommand(List.of(all.groups().get(0), all.groups().get(0)), all.dimensions(), false), applied);
        assertEquals(List.of("ip", "short", "long"), calls);
        assertTrue(service.acquire(all, applied).allowed());
        assertEquals(3, calls.size());
    }

    @Test
    void shouldNotRecordOrCallMissingNullOrBlankDimensions() {
        QuotaCommandService service = service((rule, key) -> { throw new AssertionError("must not acquire"); },
                unusedLocal(), (rule, decision) -> fail("must not observe"));
        Map<RateLimitDimension, String> dimensions = new EnumMap<>(RateLimitDimension.class);
        dimensions.put(RateLimitDimension.IP, null);
        dimensions.put(RateLimitDimension.CLIENT, " \t");
        Set<RateLimitRuleId> applied = new HashSet<>();
        assertTrue(service.acquire(new QuotaCommand(command(RedisFailureMode.CLOSED).groups(), dimensions, false), applied).allowed());
        assertTrue(applied.isEmpty());
    }

    @Test
    void shouldPreserveRawKeysAndDefensivelyCopyCommandFacts() {
        Map<RateLimitDimension, String> dimensions = new EnumMap<>(RateLimitDimension.class);
        dimensions.put(RateLimitDimension.IP, " raw:key ");
        List<RateLimitConfig> groups = new ArrayList<>(command(RedisFailureMode.CLOSED).groups());
        QuotaCommand command = new QuotaCommand(groups, dimensions, false);
        groups.clear();
        dimensions.clear();
        AtomicInteger calls = new AtomicInteger();
        service((rule, key) -> {
            calls.incrementAndGet();
            assertEquals(" raw:key ", key);
            return RateLimitDecision.allowed(rule.dimension(), key, 1);
        }, unusedLocal(), QuotaObserver.noop()).acquire(command, new HashSet<>());
        assertEquals(1, calls.get());
        assertThrows(UnsupportedOperationException.class, () -> command.dimensions().clear());
    }

    @Test
    void shouldPropagateProgrammingFailuresWithoutClaimingDegradation() {
        QuotaCommandService service = service((rule, key) -> { throw new IllegalStateException("unexpected"); },
                unusedLocal(), QuotaObserver.noop());
        Set<RateLimitRuleId> applied = new HashSet<>();
        assertThrows(IllegalStateException.class, () -> service.acquire(command(RedisFailureMode.OPEN_WITH_LOCAL), applied));
        assertTrue(applied.isEmpty());
    }

    private static QuotaCommandService service(RateLimitQuota quota, LocalRateLimitQuota local, QuotaObserver observer) {
        return new QuotaCommandService(quota, local, observer);
    }

    private static LocalRateLimitQuota unusedLocal() {
        return (rule, key) -> { throw new AssertionError("local quota must not run"); };
    }

    private static QuotaCommand command(RedisFailureMode failure) {
        return new QuotaCommand(List.of(new RateLimitConfig(List.of(
                new RateLimitRule("short", RateLimitDimension.CLIENT, 100, 1, RateLimitMode.EXACT),
                new RateLimitRule("long", RateLimitDimension.CLIENT, 900, 1, RateLimitMode.EXACT),
                new RateLimitRule("ip", RateLimitDimension.IP, 500, 1, RateLimitMode.EXACT)), failure)),
                Map.of(RateLimitDimension.CLIENT, "client", RateLimitDimension.IP, "192.0.2.1"), false);
    }
}
