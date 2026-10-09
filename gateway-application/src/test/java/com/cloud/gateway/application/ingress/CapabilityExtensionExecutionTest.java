package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.capability.policy.CapabilityPolicyAssembler;
import com.cloud.gateway.domain.capability.*;
import com.cloud.gateway.domain.capability.policy.*;
import com.cloud.gateway.domain.capability.runtime.*;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityExtensionExecutionTest {
    private static final CapabilityId ID = new CapabilityId("test-budget");
    private record Budget(int value) implements CapabilityConfig {
        public CapabilityId id() { return ID; }
    }
    private static final CapabilityPolicyComposer<Budget, Budget> CONFIGURATION = new CapabilityPolicyComposer<>() {
        public CapabilityId id() { return ID; }
        public Class<Budget> configType() { return Budget.class; }
        public Class<Budget> policyType() { return Budget.class; }
        public Budget compose(List<Budget> orderedConfigs) { return orderedConfigs.isEmpty() ? new Budget(1) : orderedConfigs.get(orderedConfigs.size() - 1); }
    };
    private record Unit(Budget budget) implements CapabilityUnit {
        public String unitId() { return ID.value(); }
    }
    private record Outcome(Budget budget) implements CapabilityOutcome {
        public Disposition disposition() { return Disposition.CONTINUE; }
    }

    @Test void shouldResolvePlanExecuteAndDeduplicateANewCapabilityWithOnlyRegistrations() {
        PlannableCapability<Budget> capability = new PlannableCapability<>() {
            public CapabilityId id() { return ID; }
            public Set<CapabilitySlot> slots() { return Set.of(CapabilitySlot.PRE_AUTH); }
            public CapabilityRepeat repeat() { return CapabilityRepeat.ONCE_PER_REQUEST; }
            public List<Unit> units(Budget configuration, CapabilityContext context) { return List.of(new Unit(configuration)); }
        };
        List<Budget> executed = new ArrayList<>();
        CapabilityExecutor<Unit> executor = new CapabilityExecutor<>() {
            public CapabilityId capability() { return ID; }
            public Class<Unit> unitType() { return Unit.class; }
            public CompletionStage<Outcome> execute(List<Unit> units) {
                executed.add(units.get(0).budget());
                return CompletableFuture.completedFuture(new Outcome(units.get(0).budget()));
            }
        };
        CapabilityExecutionCommandService service = new CapabilityExecutionCommandService(
                new CapabilityPolicyAssembler(List.of(CONFIGURATION),
                        CapabilityPolicyConfigValidator.forComposers(List.of(CONFIGURATION), List.of(), Set.of())),
                List.of(new CapabilityRegistration<>(capability, config -> config.get(Budget.class))), List.of(executor));
        CapabilityBundleId bundleId = new CapabilityBundleId("global");
        CapabilityPolicyConfig policyConfig = new CapabilityPolicyConfig(java.util.List.of(new CapabilityBundle(bundleId, List.of(new Budget(12)))),
                List.of(new CapabilityBinding(new CapabilityBindingId("global"), bundleId,
                        null, null, Set.of(), null, null, null)));
        CapabilityContext context = new CapabilityContext(CapabilitySlot.PRE_AUTH,
                new RequestEvidence("GET", "/unknown", null, null, null, null, "127.0.0.1"), null, null);
        CapabilityExecutionOutput first = service.execute(new CapabilityExecutionCommand(policyConfig, context, Set.of()))
                .toCompletableFuture().join();
        assertEquals(new Budget(12), first.capabilities().get(Budget.class));
        assertEquals(new Budget(12), assertInstanceOf(Outcome.class, first.execution().outcomes().get(0)).budget());
        CapabilityExecutionOutput second = service.executeAgainst(first.capabilities(), context, first.execution().executed())
                .toCompletableFuture().join();
        assertEquals(List.of(new Budget(12)), executed);
        assertTrue(second.execution().outcomes().isEmpty());
        assertSame(first.capabilities(), second.capabilities());
    }
}
