package com.cloud.gateway.domain.capability.policy;

import com.cloud.gateway.domain.capability.CapabilityConfig;

import static org.junit.jupiter.api.Assertions.*;

import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;
import com.cloud.gateway.domain.capability.header.*;
import com.cloud.gateway.domain.capability.ratelimit.*;
import com.cloud.gateway.domain.capability.resilience.*;
import com.cloud.gateway.domain.capability.runtime.*;
import com.cloud.framework.core.error.FrameworkException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EffectiveCapabilityPoliciesTest {
    @Test
    void shouldIndexDeclaredTypesAndDefensivelyCopyPolicies() {
        List<EffectiveCapabilityPolicies.Value<?>> values = new ArrayList<>(List.of(
                new EffectiveCapabilityPolicies.Value<>(CharSequence.class, "value")));
        EffectiveCapabilityPolicies policies = new EffectiveCapabilityPolicies(values);
        values.clear();
        assertEquals("value", policies.get(CharSequence.class));
        assertThrows(FrameworkException.class, () -> policies.get(String.class));
        assertThrows(FrameworkException.class, () -> policies.get(null));
        assertThrows(FrameworkException.class, () -> new EffectiveCapabilityPolicies(List.of(
                new EffectiveCapabilityPolicies.Value<>(String.class, "a"),
                new EffectiveCapabilityPolicies.Value<>(String.class, "b"))));
        assertThrows(FrameworkException.class, () -> new EffectiveCapabilityPolicies.Value<>(null, "value"));
        assertEquals(policies, new EffectiveCapabilityPolicies(List.of(
                new EffectiveCapabilityPolicies.Value<>(CharSequence.class, "value"))));
    }

    private final CapabilityPolicyAssembler assembler = BuiltInCapabilityCatalog.assembler();

    @Test
    void shouldResolvePresentPoliciesEvenWhenNoConfigIsDeclared() {
        EffectiveCapabilityPolicies policies = assembler
                .assemble(CapabilityPolicyConfig.empty(), null, null);
        assertTrue(policies.get(EffectiveRateLimitPolicy.class).configs().isEmpty());
        assertNotNull(policies.get(EffectiveHeaderPolicy.class).config());
        assertFalse(policies.get(EffectiveTimeoutPolicy.class).configured());
        assertTrue(new TimeoutCapability().units(policies.get(EffectiveTimeoutPolicy.class), null).isEmpty());
    }

    @Test
    void shouldKeepAccumulatedGroupsAndTheirOwnFailureModes() {
        RateLimitConfig first = new RateLimitConfig(List.of(
                new RateLimitRule("first", RateLimitDimension.IP, 10, 1, RateLimitMode.EXACT)), RedisFailureMode.CLOSED);
        RateLimitConfig second = new RateLimitConfig(List.of(
                new RateLimitRule("second", RateLimitDimension.IP, 20, 2, RateLimitMode.EXACT)), RedisFailureMode.OPEN_WITH_LOCAL);
        EffectiveRateLimitPolicy policy = RateLimitPolicyComposer.INSTANCE.compose(List.of(first, second));
        assertEquals(List.of(first, second), policy.configs());
        List<ApplicableQuota> quotas = RateLimitEvaluator.applicable(
                policy.configs(), Map.of(RateLimitDimension.IP, "127.0.0.1"), true, Set.of());
        assertEquals(List.of(RedisFailureMode.CLOSED, RedisFailureMode.OPEN_WITH_LOCAL),
                quotas.stream().map(ApplicableQuota::failureMode).toList());
    }

    @Test
    void shouldCarryComposedConfigurationsWithoutRepeatingPolicyConfigRules() {
        RateLimitConfig first = new RateLimitConfig(List.of(
                new RateLimitRule("shared", RateLimitDimension.IP, 10, 1, RateLimitMode.EXACT)), RedisFailureMode.CLOSED);
        RateLimitConfig second = new RateLimitConfig(List.of(
                new RateLimitRule("shared", RateLimitDimension.IP, 20, 2, RateLimitMode.EXACT)), RedisFailureMode.OPEN_WITH_LOCAL);
        List<RateLimitConfig> configs = List.of(first, second);
        assertEquals(configs, new EffectiveRateLimitPolicy(configs).configs());
        assertEquals(configs, RateLimitPolicyComposer.INSTANCE.compose(configs).configs());
    }

    @Test
    void shouldSelectOneHeaderAndTimeoutConfigAfterReplacement() {
        HeaderConfig first = HeaderConfig.defaults(null);
        HeaderConfig last = new HeaderConfig(Set.of(), Map.of("X-Gateway-Test", "last"), Set.of(), null);
        TimeoutConfig shortBudget = new TimeoutConfig(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3));
        TimeoutConfig longBudget = new TimeoutConfig(Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(6));
        EffectiveHeaderPolicy headers = HeaderPolicyComposer.INSTANCE.compose(List.of(first, last));
        EffectiveTimeoutPolicy timeout = TimeoutPolicyComposer.INSTANCE.compose(List.of(shortBudget, longBudget));
        assertSame(last, headers.config());
        assertSame(longBudget, timeout.config());
        assertTrue(timeout.configured());
    }

    @Test
    void shouldCopyGroupsAndRejectMissingPoliciesAndWrongKeyTypes() {
        List<RateLimitConfig> configs = new ArrayList<>(List.of(new RateLimitConfig(List.of(), null)));
        EffectiveRateLimitPolicy policy = new EffectiveRateLimitPolicy(configs);
        configs.clear();
        assertEquals(1, policy.configs().size());
        assertThrows(UnsupportedOperationException.class, () -> policy.configs().clear());
        assertThrows(FrameworkException.class, () -> new EffectiveRateLimitPolicy(null));
        List<RateLimitConfig> missingElement = new ArrayList<>();
        missingElement.add(null);
        assertThrows(FrameworkException.class, () -> new EffectiveRateLimitPolicy(missingElement));
        assertTrue(new EffectiveRateLimitPolicy(List.of()).configs().isEmpty());
        assertThrows(FrameworkException.class, () -> new EffectiveHeaderPolicy(null));
        assertThrows(FrameworkException.class, () -> new CapabilityPlanningEntry<>(new TimeoutCapability(), null));
        assertThrows(FrameworkException.class, () -> CapabilityConfig.requireType(RateLimitPolicyComposer.INSTANCE.id(), RateLimitPolicyComposer.INSTANCE.configType(), HeaderConfig.defaults(null)));
        assertThrows(FrameworkException.class, () -> CapabilityConfig.requireType(HeaderPolicyComposer.INSTANCE.id(), HeaderPolicyComposer.INSTANCE.configType(), RateLimitConfig.disabled()));
        assertThrows(FrameworkException.class, () -> new EffectiveCapabilityPolicies.Value<>(
                EffectiveTimeoutPolicy.class, null));
    }

    @Test
    void shouldNotTurnUnconfiguredTimeoutIntoAnExecutionUnitOrOutcome() {
        EffectiveTimeoutPolicy policy = EffectiveTimeoutPolicy.unconfigured();
        assertThrows(FrameworkException.class, () -> new TimeoutCapability.Unit(policy));
        assertThrows(FrameworkException.class, () -> new TimeoutCapability.Outcome(policy));
    }

    @Test
    void shouldRejectNullConfigurationsEvenWhenALaterValueWouldReplaceThem() {
        assertThrows(FrameworkException.class, () -> HeaderPolicyComposer.INSTANCE.compose(null));
        assertThrows(FrameworkException.class, () -> TimeoutPolicyComposer.INSTANCE.compose(null));
        assertThrows(FrameworkException.class, () -> RateLimitPolicyComposer.INSTANCE.compose(null));
        List<HeaderConfig> headers = new ArrayList<>();
        headers.add(null);
        headers.add(HeaderConfig.defaults(null));
        assertThrows(FrameworkException.class, () -> HeaderPolicyComposer.INSTANCE.compose(headers));
        List<TimeoutConfig> timeouts = new ArrayList<>();
        timeouts.add(null);
        timeouts.add(new TimeoutConfig(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3)));
        assertThrows(FrameworkException.class, () -> TimeoutPolicyComposer.INSTANCE.compose(timeouts));
        List<RateLimitConfig> limits = new ArrayList<>();
        limits.add(null);
        assertThrows(FrameworkException.class, () -> RateLimitPolicyComposer.INSTANCE.compose(limits));
    }

}
