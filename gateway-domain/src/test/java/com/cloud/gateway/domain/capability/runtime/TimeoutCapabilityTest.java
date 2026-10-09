package com.cloud.gateway.domain.capability.runtime;

import com.cloud.gateway.domain.capability.resilience.EffectiveTimeoutPolicy;

import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;

import com.cloud.gateway.domain.capability.resilience.TimeoutConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.capability.policy.CapabilityBinding;
import com.cloud.gateway.domain.capability.policy.CapabilityBindingId;
import com.cloud.gateway.domain.capability.policy.CapabilityBundle;
import com.cloud.gateway.domain.capability.policy.CapabilityBundleId;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
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
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TimeoutCapabilityTest {
    private static final AppId APP = new AppId("app");
    private static final Endpoint ENDPOINT = new Endpoint(new EndpointId("read"), new ServiceId("service"),
            new RouteId("route"), List.of(new EndpointMatcher("GET", "/read")), Set.of(), EndpointStatus.ENABLED,
            new EndpointAccess(ClientVerificationRequirement.OPTIONAL, SubjectAuthenticationRequirement.ANONYMOUS,
                    Set.of(), Set.of()));
    private static final RequestEvidence EVIDENCE = new RequestEvidence("GET", "/read", null, APP, null, null, "ip");
    private static final AccessGrantedContext GRANTED =
            new AccessGrantedContext(new VerifiedApp(APP, "web"), null, null);
    private static final TimeoutConfig POLICY = new TimeoutConfig(
            Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(5));

    @Test
    void shouldPlanExactlyOneUnitBeforeForwarding() {
        CapabilityPlan plan = planner().plan(config(POLICY), forward(), Set.of());
        assertEquals(1, plan.steps().size());
        assertEquals(TimeoutCapability.ID, plan.steps().get(0).capability());
        assertEquals(List.of(TimeoutCapability.ID.value()),
                plan.steps().get(0).units().stream().map(CapabilityUnit::unitId).toList());
    }

    @Test
    void shouldPlanNothingWhenNoNodeIsConfigured() {
        assertTrue(planner().plan(config(null), forward(), Set.of()).steps().isEmpty());
    }

    @Test
    void shouldNotRunInIngressSlots() {
        CapabilityContext postAuth = new CapabilityContext(CapabilitySlot.POST_AUTH, EVIDENCE, ENDPOINT, GRANTED);
        assertTrue(planner().plan(config(POLICY), postAuth, Set.of()).steps().isEmpty());
    }

    /** A resolved timeout must reach its typed planning source. */
    @Test
    void shouldReachThePlannerThroughTheEffectiveConfiguration() {
        CapabilityBundleId bundle = new CapabilityBundleId("resilience");
        CapabilityPolicyConfig policyConfig = new CapabilityPolicyConfig(java.util.List.of(new CapabilityBundle(bundle, java.util.List.of(POLICY))),
                List.of(new CapabilityBinding(new CapabilityBindingId("global"), bundle, null, null, Set.of(),
                        null, null, null)));
        CapabilityContext context = forward();
        CapabilityPlan plan = planner().plan(
                config(BuiltInCapabilityCatalog.assembler().assemble(policyConfig, context.endpoint(), context.granted()).get(EffectiveTimeoutPolicy.class).config()), context, Set.of());
        assertEquals(1, plan.steps().size());
        assertEquals(TimeoutCapability.ID, plan.steps().get(0).capability());
    }

    private static CapabilityPlanner planner() {
        return new CapabilityPlanner();
    }

    private static CapabilityContext forward() {
        return new CapabilityContext(CapabilitySlot.FORWARD, EVIDENCE, ENDPOINT, GRANTED);
    }

    private static List<CapabilityPlanningEntry<?>> config(TimeoutConfig policy) {
        return List.of(new CapabilityPlanningEntry<>(new TimeoutCapability(), new EffectiveTimeoutPolicy(policy)));
    }
}
