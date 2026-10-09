package com.cloud.gateway.domain.capability.runtime;

import com.cloud.gateway.domain.capability.resilience.EffectiveTimeoutPolicy;

import com.cloud.gateway.domain.capability.ratelimit.EffectiveRateLimitPolicy;

import com.cloud.gateway.domain.capability.header.EffectiveHeaderPolicy;

import com.cloud.gateway.domain.capability.policy.EffectiveCapabilityPolicies;

import com.cloud.gateway.domain.capability.header.HeaderConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.gateway.domain.app.AppId;
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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HeaderApplyCapabilityTest {
    private static final AppId APP = new AppId("app");
    private static final Endpoint ENDPOINT = new Endpoint(new EndpointId("read"), new ServiceId("service"),
            new RouteId("route"), List.of(new EndpointMatcher("GET", "/read")), Set.of(), EndpointStatus.ENABLED,
            new EndpointAccess(ClientVerificationRequirement.OPTIONAL, SubjectAuthenticationRequirement.ANONYMOUS,
                    Set.of(), Set.of()));
    private static final RequestEvidence EVIDENCE = new RequestEvidence("GET", "/read", null, APP, null, null, "ip");
    private static final AccessGrantedContext GRANTED =
            new AccessGrantedContext(new VerifiedApp(APP, "web"), null, null);
    private static final HeaderConfig POLICY = HeaderConfig.defaults(null);

    @Test
    void shouldPlanOneUnitBeforeForwarding() {
        CapabilityPlan plan = planner().plan(nodes(), forward(), Set.of());

        assertEquals(1, plan.steps().size());
        assertEquals(HeaderApplyCapability.ID, plan.steps().get(0).capability());
        assertEquals(List.of(HeaderApplyCapability.ID.value()),
                plan.steps().get(0).units().stream().map(CapabilityUnit::unitId).toList());
    }

    /** The plan is the unit's whole content, and the outcome hands that same plan on unchanged. */
    @Test
    void shouldCarryThePlanThroughTheUnitAndTheOutcome() {
        CapabilityPlan plan = planner().plan(nodes(), forward(), Set.of());
        HeaderApplyCapability.Unit unit = (HeaderApplyCapability.Unit) plan.steps().get(0).units().get(0);

        assertSame(POLICY, unit.policy().config());
        assertSame(POLICY, new HeaderApplyCapability.Outcome(new EffectiveHeaderPolicy(POLICY)).policy().config());
        assertEquals(CapabilityOutcome.Disposition.CONTINUE,
                new HeaderApplyCapability.Outcome(new EffectiveHeaderPolicy(POLICY)).disposition());
    }

    @Test
    void shouldRejectMissingMandatoryHeaderConfiguration() {
        org.junit.jupiter.api.Assertions.assertThrows(com.cloud.framework.core.error.FrameworkException.class,
                () -> new EffectiveCapabilityPolicies(java.util.List.of(new EffectiveCapabilityPolicies.Value<>(EffectiveRateLimitPolicy.class, new EffectiveRateLimitPolicy(List.of())), new EffectiveCapabilityPolicies.Value<>(EffectiveTimeoutPolicy.class, new EffectiveTimeoutPolicy(null)), new EffectiveCapabilityPolicies.Value<>(EffectiveHeaderPolicy.class, null))));
    }

    /**
     * The plan is applied just before the call is issued, not in the ingress slots.
     */
    @Test
    void shouldRunOnlyInTheForwardSlot() {
        CapabilityContext preAuth = new CapabilityContext(CapabilitySlot.PRE_AUTH, EVIDENCE, null, null);
        CapabilityContext postAuth = new CapabilityContext(CapabilitySlot.POST_AUTH, EVIDENCE, ENDPOINT, GRANTED);

        assertTrue(planner().plan(nodes(), preAuth, Set.of()).steps().isEmpty());
        assertTrue(planner().plan(nodes(), postAuth, Set.of()).steps().isEmpty());
    }

    /** Request-wide, so a plan cannot be applied twice within one request. */
    @Test
    void shouldNotPlanTheSamePlanTwice() {
        CapabilityPlan first = planner().plan(nodes(), forward(), Set.of());

        CapabilityPlan again = planner().plan(nodes(), forward(), Set.copyOf(first.steps().get(0).keys()));

        assertTrue(again.steps().isEmpty());
    }

    private static CapabilityPlanner planner() {
        return new CapabilityPlanner();
    }

    private static List<CapabilityPlanningEntry<?>> nodes() {
        return List.of(new CapabilityPlanningEntry<>(new HeaderApplyCapability(), new EffectiveHeaderPolicy(POLICY)));
    }

    private static CapabilityContext forward() {
        return new CapabilityContext(CapabilitySlot.FORWARD, EVIDENCE, ENDPOINT, GRANTED);
    }
}
