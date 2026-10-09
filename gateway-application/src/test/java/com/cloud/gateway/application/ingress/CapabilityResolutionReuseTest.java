package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.capability.resilience.EffectiveTimeoutPolicy;

import com.cloud.gateway.domain.capability.ratelimit.EffectiveRateLimitPolicy;

import com.cloud.gateway.domain.capability.header.EffectiveHeaderPolicy;

import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
import com.cloud.gateway.domain.capability.policy.EffectiveCapabilityPolicies;
import com.cloud.gateway.domain.capability.runtime.CapabilityContext;
import com.cloud.gateway.domain.capability.runtime.CapabilitySlot;
import com.cloud.gateway.domain.capability.runtime.RateLimitCapability;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import com.cloud.gateway.domain.ingress.access.VerifiedApp;
import com.cloud.gateway.domain.app.AppId;
import java.util.List;
import java.util.Set;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import com.cloud.gateway.domain.capability.resilience.TimeoutConfig;
import com.cloud.gateway.domain.capability.header.HeaderConfig;
import com.cloud.gateway.domain.capability.runtime.TimeoutCapability;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * The forward slot sees the same snapshot, endpoint and granted identity as admission, so it reuses
 * its resolution instead of repeating it. Asserting identity is what makes that observable: a second
 * resolve would produce an equal but different instance.
 */
class CapabilityResolutionReuseTest {
    @Test
    void shouldIsolateOverlappingRequestsAndNotRetainThePreviousConfiguration() {
        List<CompletableFuture<TimeoutCapability.Outcome>> pending = new ArrayList<>();
        List<TimeoutConfig> seen = new ArrayList<>();
        CapabilityExecutor<TimeoutCapability.Unit> executor = new CapabilityExecutor<>() {
            public CapabilityId capability() { return TimeoutCapability.ID; }
            public Class<TimeoutCapability.Unit> unitType() { return TimeoutCapability.Unit.class; }
            public CompletionStage<TimeoutCapability.Outcome> execute(List<TimeoutCapability.Unit> units) {
                seen.add(units.get(0).policy().config());
                CompletableFuture<TimeoutCapability.Outcome> completion = new CompletableFuture<>();
                pending.add(completion);
                return completion;
            }
        };
        CapabilityExecutionCommandService service = new CapabilityExecutionCommandService(BuiltInCapabilityCatalog.assembler(),
                List.of(new CapabilityRegistration<>(new TimeoutCapability(), config -> config.get(EffectiveTimeoutPolicy.class))),
                List.of(executor));
        TimeoutConfig firstPolicy = new TimeoutConfig(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3));
        TimeoutConfig secondPolicy = new TimeoutConfig(Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(6));
        EffectiveCapabilityPolicies first = new EffectiveCapabilityPolicies(java.util.List.of(new EffectiveCapabilityPolicies.Value<>(EffectiveRateLimitPolicy.class, new EffectiveRateLimitPolicy(List.of())), new EffectiveCapabilityPolicies.Value<>(EffectiveTimeoutPolicy.class, new EffectiveTimeoutPolicy(firstPolicy)), new EffectiveCapabilityPolicies.Value<>(EffectiveHeaderPolicy.class, new EffectiveHeaderPolicy(HeaderConfig.defaults(null)))));
        EffectiveCapabilityPolicies second = new EffectiveCapabilityPolicies(java.util.List.of(new EffectiveCapabilityPolicies.Value<>(EffectiveRateLimitPolicy.class, new EffectiveRateLimitPolicy(List.of())), new EffectiveCapabilityPolicies.Value<>(EffectiveTimeoutPolicy.class, new EffectiveTimeoutPolicy(secondPolicy)), new EffectiveCapabilityPolicies.Value<>(EffectiveHeaderPolicy.class, new EffectiveHeaderPolicy(HeaderConfig.defaults(null)))));
        CompletableFuture<CapabilityExecutionOutput> firstRequest = service.executeAgainst(first, forward(), Set.of()).toCompletableFuture();
        CompletableFuture<CapabilityExecutionOutput> secondRequest = service.executeAgainst(second, forward(), Set.of()).toCompletableFuture();
        assertEquals(List.of(firstPolicy, secondPolicy), seen);
        pending.get(1).complete(new TimeoutCapability.Outcome(new EffectiveTimeoutPolicy(secondPolicy)));
        assertFalse(firstRequest.isDone());
        assertSame(second, secondRequest.join().capabilities());
        assertSame(secondPolicy, assertInstanceOf(TimeoutCapability.Outcome.class,
                secondRequest.join().execution().outcomes().get(0)).policy().config());
        pending.get(0).complete(new TimeoutCapability.Outcome(new EffectiveTimeoutPolicy(firstPolicy)));
        assertSame(first, firstRequest.join().capabilities());
        assertSame(firstPolicy, assertInstanceOf(TimeoutCapability.Outcome.class,
                firstRequest.join().execution().outcomes().get(0)).policy().config());
        EffectiveCapabilityPolicies absent = new EffectiveCapabilityPolicies(java.util.List.of(new EffectiveCapabilityPolicies.Value<>(EffectiveRateLimitPolicy.class, new EffectiveRateLimitPolicy(List.of())), new EffectiveCapabilityPolicies.Value<>(EffectiveTimeoutPolicy.class, new EffectiveTimeoutPolicy(null)), new EffectiveCapabilityPolicies.Value<>(EffectiveHeaderPolicy.class, new EffectiveHeaderPolicy(HeaderConfig.defaults(null)))));
        assertTrue(service.executeAgainst(absent, forward(), Set.of()).toCompletableFuture().join()
                .execution().outcomes().isEmpty());
        assertEquals(2, seen.size());
    }

    private static final AppId APP = new AppId("app");
    private static final Endpoint ENDPOINT = new Endpoint(new EndpointId("read"), new ServiceId("service"),
            new RouteId("route"), List.of(new EndpointMatcher("GET", "/read")), Set.of(), EndpointStatus.ENABLED,
            new EndpointAccess(ClientVerificationRequirement.OPTIONAL, SubjectAuthenticationRequirement.ANONYMOUS,
                    Set.of(), Set.of()));
    private static final RequestEvidence EVIDENCE = new RequestEvidence("GET", "/read", null, APP, null, null, "ip");
    private static final AccessGrantedContext GRANTED =
            new AccessGrantedContext(new VerifiedApp(APP, "web"), null, null);

    @Test
    void shouldPlanAgainstTheResolutionItWasHanded() {
        CapabilityExecutionCommandService service = new CapabilityExecutionCommandService(
                BuiltInCapabilityCatalog.assembler(), List.of(new CapabilityRegistration<>(new RateLimitCapability(), config -> config.get(EffectiveRateLimitPolicy.class))),
                List.of(new RateLimitCapabilityExecutor(quotas -> {
                    throw new AssertionError("FORWARD must not acquire quota");
                })));
        EffectiveCapabilityPolicies resolved =
                BuiltInCapabilityCatalog.assembler().assemble(CapabilityPolicyConfig.empty(), forward().endpoint(), forward().granted());

        CapabilityExecutionOutput output = service.executeAgainst(resolved, forward(), Set.of())
                .toCompletableFuture().join();

        assertSame(resolved, output.capabilities(), "the caller's resolution must be reused, not repeated");
    }

    private static CapabilityContext forward() {
        return new CapabilityContext(CapabilitySlot.FORWARD, EVIDENCE, ENDPOINT, GRANTED);
    }
}
