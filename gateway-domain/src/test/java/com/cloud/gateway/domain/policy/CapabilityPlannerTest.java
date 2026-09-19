package com.cloud.gateway.domain.policy;

import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.*;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import com.cloud.gateway.domain.ingress.access.app.VerifiedApp;
import com.cloud.gateway.domain.policy.bundle.*;
import com.cloud.gateway.domain.policy.capability.*;
import com.cloud.gateway.domain.policy.execution.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityPlannerTest {
    private static final AppId APP = new AppId("app");
    private static final Endpoint ENDPOINT = new Endpoint(new EndpointId("read"), new ServiceId("service"),
            new RouteId("route"), List.of(new EndpointMatcher("GET", "/read")), Set.of(), EndpointStatus.ENABLED,
            new EndpointAccess(ClientVerificationRequirement.OPTIONAL, SubjectAuthenticationRequirement.ANONYMOUS,
                    Set.of(), Set.of()));
    private static final RequestEvidence EVIDENCE = new RequestEvidence("GET", "/read", null,
            APP, new ClientId("declared"), null, "ip");
    private static final CapabilityContext BEFORE = new CapabilityContext(CapabilitySlot.PRE_AUTH, EVIDENCE, ENDPOINT, null);
    private static final CapabilityContext AFTER = new CapabilityContext(CapabilitySlot.POST_AUTH, EVIDENCE, ENDPOINT,
            new AccessGrantedContext(new VerifiedApp(APP, "web"), null, null));

    @Test
    void shouldExecuteRequestOnceAndSlotOnceWithoutKnowingCapabilityType() {
        CapabilityPlanner planner = new CapabilityPlanner(List.of(
                definition("first", CapabilityRepeat.ONCE_PER_REQUEST, Set.of(), Set.of()),
                definition("second", CapabilityRepeat.ONCE_PER_SLOT, Set.of(), Set.of())));
        CapabilityPlan pre = planner.plan(resolved(), BEFORE, Set.of());
        assertEquals(List.of("first", "second"), pre.steps().stream().map(CapabilityPlan.Step::capability).toList());
        Set<CapabilityExecutionKey> executed = pre.steps().stream().flatMap(step -> step.keys().stream()).collect(Collectors.toSet());
        CapabilityPlan post = planner.plan(resolved(), AFTER, executed);
        assertEquals(List.of("second"), post.steps().stream().map(CapabilityPlan.Step::capability).toList());
        assertEquals(CapabilitySlot.POST_AUTH, post.steps().get(0).keys().get(0).slot());
        executed.addAll(post.steps().get(0).keys());
        assertTrue(planner.plan(resolved(), AFTER, executed).steps().isEmpty());
    }

    @Test
    void shouldRequireFactsAtBothDefinitionAndUnitLevel() {
        CapabilityPlanner planner = new CapabilityPlanner(List.of(
                definition("after", CapabilityRepeat.ONCE_PER_REQUEST, Set.of(CapabilityFact.ACCESS_GRANTED), Set.of()),
                definition("subject", CapabilityRepeat.ONCE_PER_REQUEST, Set.of(), Set.of(CapabilityFact.AUTHENTICATED_SUBJECT)),
                definition("client", CapabilityRepeat.ONCE_PER_REQUEST, Set.of(CapabilityFact.VERIFIED_CLIENT), Set.of())));
        assertTrue(planner.plan(resolved(), BEFORE, Set.of()).steps().isEmpty());
        assertEquals(List.of("after"), planner.plan(resolved(), AFTER, Set.of()).steps().stream()
                .map(CapabilityPlan.Step::capability).toList());
        assertTrue(AFTER.facts().contains(CapabilityFact.ACCESS_GRANTED));
        assertFalse(AFTER.facts().contains(CapabilityFact.AUTHENTICATED_SUBJECT));
        assertFalse(BEFORE.facts().contains(CapabilityFact.VERIFIED_CLIENT));
        assertThrows(UnsupportedOperationException.class, () -> AFTER.facts().clear());
    }

    @Test
    void shouldDeferIdentityBoundIpRuleAndNeverDeductGlobalRuleTwice() {
        CapabilityBundleId global = new CapabilityBundleId("global");
        CapabilityBundleId app = new CapabilityBundleId("app");
        CapabilityComposition composition = new CapabilityComposition(Map.of(global, bundle(global), app, bundle(app)),
                List.of(binding(global, null), binding(app, APP)));
        CapabilityResolver resolver = new CapabilityResolver();
        CapabilityPlanner planner = new CapabilityPlanner(List.of(new RateLimitCapability()));
        CapabilityPlan pre = planner.plan(resolver.resolve(composition, BEFORE), BEFORE, Set.of());
        assertEquals(List.of("global"), pre.steps().get(0).units().stream().map(CapabilityUnit::unitId).toList());
        CapabilityPlan post = planner.plan(resolver.resolve(composition, AFTER), AFTER, Set.copyOf(pre.steps().get(0).keys()));
        assertEquals(List.of("app"), post.steps().get(0).units().stream().map(CapabilityUnit::unitId).toList());
        assertEquals("ip", ((RateLimitCapability.Unit) post.steps().get(0).units().get(0)).quota().key());
        CapabilityContext unknown = new CapabilityContext(CapabilitySlot.PRE_AUTH, EVIDENCE, null, null);
        assertEquals(1, resolver.resolve(composition, unknown).rateLimits().size());
    }

    @Test
    void shouldRejectImpossibleContextsAndDuplicateRegistration() {
        assertThrows(DomainException.class, () -> new CapabilityContext(CapabilitySlot.POST_AUTH, EVIDENCE, ENDPOINT, null));
        assertThrows(DomainException.class, () -> new CapabilityContext(CapabilitySlot.PRE_AUTH, EVIDENCE, ENDPOINT, AFTER.granted()));
        CapabilityDefinition definition = definition("same", CapabilityRepeat.ONCE_PER_REQUEST, Set.of(), Set.of());
        assertThrows(DomainException.class, () -> new CapabilityPlanner(List.of(definition, definition)));
    }

    @Test
    void shouldSkipUnsupportedSlot() {
        CapabilityDefinition postOnly = new CapabilityDefinition() {
            public String capability() { return "post"; }
            public Set<CapabilitySlot> slots() { return Set.of(CapabilitySlot.POST_AUTH); }
            public CapabilityRepeat repeat() { return CapabilityRepeat.ONCE_PER_REQUEST; }
            public List<Unit> units(ResolvedCapabilities resolved, CapabilityContext context) { return List.of(new Unit("unit", Set.of())); }
        };
        CapabilityPlanner planner = new CapabilityPlanner(List.of(postOnly));
        assertTrue(planner.plan(resolved(), BEFORE, Set.of()).steps().isEmpty());
        assertEquals(1, planner.plan(resolved(), AFTER, Set.of()).steps().size());
    }

    private static CapabilityDefinition definition(String id, CapabilityRepeat repeat, Set<CapabilityFact> facts,
            Set<CapabilityFact> unitFacts) {
        return new CapabilityDefinition() {
            public String capability() { return id; }
            public Set<CapabilitySlot> slots() { return Set.of(CapabilitySlot.PRE_AUTH, CapabilitySlot.POST_AUTH); }
            public CapabilityRepeat repeat() { return repeat; }
            public Set<CapabilityFact> requiredFacts() { return facts; }
            public List<Unit> units(ResolvedCapabilities resolved, CapabilityContext context) {
                return List.of(new Unit("shared-id", unitFacts), new Unit("shared-id", unitFacts));
            }
        };
    }

    private record Unit(String unitId, Set<CapabilityFact> requiredFacts) implements CapabilityUnit {
        private Unit {
            requiredFacts = Set.copyOf(requiredFacts);
        }
    }

    private static ResolvedCapabilities resolved() {
        return new CapabilityResolver().resolve(CapabilityComposition.empty(), BEFORE);
    }

    private static CapabilityBundle bundle(CapabilityBundleId id) {
        return new CapabilityBundle(id, new RateLimitRules(List.of(new RateLimitRule(id.value(), RateLimitDimension.IP,
                10, 1, RateLimitMode.EXACT)), null), null, null, null, null);
    }

    private static CapabilityBinding binding(CapabilityBundleId id, AppId app) {
        return new CapabilityBinding(new CapabilityBindingId(id.value()), id, null, null, Set.of(), app, null, null);
    }
}
