package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.endpoint.*;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import com.cloud.gateway.domain.ingress.access.app.VerifiedApp;
import com.cloud.gateway.domain.policy.bundle.*;
import com.cloud.gateway.domain.policy.capability.*;
import com.cloud.gateway.domain.policy.execution.*;
import com.cloud.gateway.domain.policy.header.HeaderPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityExecutionCommandServiceTest {
    private static final RequestEvidence EVIDENCE = new RequestEvidence("GET", "/test", null,
            new AppId("app"), null, null, "ip");
    private static final CapabilityContext BEFORE = new CapabilityContext(CapabilitySlot.PRE_AUTH, EVIDENCE, null, null);

    @Test
    void shouldRunInPlanOrderAndStopAfterRejectedBatch() {
        List<String> calls = new ArrayList<>();
        CapabilityExecutionCommandService service = service(List.of(
                definition("first", "one"), definition("second", "two", "three"), definition("third", "four")),
                List.of(
                        executor("first", units -> { calls.add("first"); return CompletableFuture.completedFuture(new Outcome(true)); }),
                        executor("second", units -> { calls.add("second"); assertEquals(2, units.size()); return CompletableFuture.completedFuture(new Outcome(false)); }),
                        executor("third", units -> { throw new AssertionError("must short circuit"); })));
        CapabilityExecutionKey previous = new CapabilityExecutionKey("previous", "unit", null);
        CapabilityExecutionOutput output = service.execute(command(Set.of(previous))).toCompletableFuture().join();
        CapabilityExecution report = output.execution();
        assertEquals(List.of("first", "second"), calls);
        assertFalse(report.allowed());
        assertEquals(4, report.executed().size());
        assertEquals(2, report.outcomes().size());
        assertTrue(report.executed().contains(previous));
        assertNotNull(output.capabilities().headers());
    }

    @Test
    void shouldValidateAllExecutorsBeforeAnySideEffect() {
        CapabilityExecutor<Unit> first = executor("first", units -> {
            throw new AssertionError("validation must precede execution");
        });
        CapabilityExecutionCommandService missing = service(
                List.of(definition("first", "one"), definition("missing", "two")), List.of(first));
        assertThrows(CompletionException.class, () -> missing.execute(command(Set.of())).toCompletableFuture().join());
        CapabilityDefinition wrongType = definition("first", ignored -> List.of(new OtherUnit("bad")));
        CapabilityExecutionCommandService mismatch = service(List.of(wrongType), List.of(first));
        assertInstanceOf(ClassCastException.class, assertThrows(CompletionException.class,
                () -> mismatch.execute(command(Set.of())).toCompletableFuture().join()).getCause());
    }

    @Test
    void shouldCancelPendingExecutionWithoutStartingNextCapability() {
        CompletableFuture<Outcome> pending = new CompletableFuture<>();
        CapabilityExecutionCommandService service = service(List.of(definition("first", "one"), definition("second", "two")),
                List.of(executor("first", units -> pending),
                        executor("second", units -> { throw new AssertionError("cancelled"); })));
        CompletableFuture<CapabilityExecutionOutput> result = service.execute(command(Set.of())).toCompletableFuture();
        assertFalse(result.isDone());
        result.cancel(true);
        assertTrue(pending.isCancelled());
    }

    @Test
    void shouldPropagateFailureAndRejectDuplicateRegistration() {
        IllegalStateException failure = new IllegalStateException("unavailable");
        CapabilityExecutor<Unit> executor = executor("first", units -> CompletableFuture.failedFuture(failure));
        CapabilityExecutionCommandService service = service(List.of(definition("first", "one")), List.of(executor));
        assertSame(failure, assertThrows(CompletionException.class,
                () -> service.execute(command(Set.of())).toCompletableFuture().join()).getCause());
        assertThrows(IllegalArgumentException.class, () -> service(List.of(), List.of(executor, executor)));
        assertTrue(service(List.of(), List.of()).execute(command(Set.of())).toCompletableFuture().join().execution().allowed());
    }

    @Test
    void shouldReturnTheExactResolvedNodesConsumedByThePlannerEvenOnDenial() {
        AtomicReference<ResolvedCapabilities> planned = new AtomicReference<>();
        CapabilityDefinition definition = definition("test", resolved -> {
            planned.set(resolved);
            return List.of(new Unit("unit"));
        });
        HeaderPolicy headers = new HeaderPolicy(Set.of(), Map.of("X-Gateway-Test", "selected"), Set.of(), null);
        CapabilityBundleId id = new CapabilityBundleId("headers");
        CapabilityComposition composition = new CapabilityComposition(
                Map.of(id, new CapabilityBundle(id, null, null, null, null, headers)),
                List.of(new CapabilityBinding(new CapabilityBindingId("global"), id, null, null, Set.of(), null, null, null)));
        CapabilityExecutionCommandService service = service(List.of(definition),
                List.of(executor("test", units -> CompletableFuture.completedFuture(new Outcome(false)))));
        CapabilityExecutionOutput output = service.execute(new CapabilityExecutionCommand(composition, BEFORE, Set.of()))
                .toCompletableFuture().join();
        assertSame(planned.get(), output.capabilities());
        assertSame(headers, output.capabilities().headers());
        assertFalse(output.execution().allowed());
    }

    @Test
    void shouldResolveAndPlanBothSlotsWithoutRepeatingIpQuota() {
        AppId app = EVIDENCE.declaredAppId();
        Endpoint endpoint = new Endpoint(new EndpointId("test"), new ServiceId("service"), new RouteId("route"),
                List.of(new EndpointMatcher("GET", "/test")), Set.of(), EndpointStatus.ENABLED,
                new EndpointAccess(ClientVerificationRequirement.OPTIONAL, SubjectAuthenticationRequirement.ANONYMOUS,
                        Set.of(), Set.of()));
        CapabilityContext after = new CapabilityContext(CapabilitySlot.POST_AUTH, EVIDENCE, endpoint,
                new AccessGrantedContext(new VerifiedApp(app, "web"), null, null));
        CapabilityBundleId id = new CapabilityBundleId("limits");
        CapabilityComposition composition = new CapabilityComposition(Map.of(id, new CapabilityBundle(id,
                new RateLimitRules(List.of(
                        new RateLimitRule("ip", RateLimitDimension.IP, 10, 1, RateLimitMode.EXACT),
                        new RateLimitRule("app", RateLimitDimension.APP, 10, 1, RateLimitMode.EXACT)), null),
                null, null, null, null)), List.of(
                new CapabilityBinding(new CapabilityBindingId("global"), id, null, null, Set.of(), null, null, null)));
        List<List<String>> batches = new ArrayList<>();
        CapabilityExecutionCommandService service = service(List.of(new RateLimitCapability()), List.of(
                new RateLimitCapabilityExecutor(quotas -> {
                    batches.add(quotas.stream().map(quota -> quota.rule().ruleId().value()).toList());
                    return CompletableFuture.completedFuture(RateLimitDecision.allowed(null, null, 1));
                })));
        CapabilityExecutionOutput pre = service.execute(new CapabilityExecutionCommand(composition, BEFORE, Set.of()))
                .toCompletableFuture().join();
        CapabilityExecutionOutput post = service.execute(new CapabilityExecutionCommand(composition, after, pre.execution().executed()))
                .toCompletableFuture().join();
        assertEquals(List.of(List.of("ip"), List.of("app")), batches);
        assertEquals(2, post.execution().executed().size());
        CapabilityExecutionOutput repeat = service.execute(new CapabilityExecutionCommand(composition, after, post.execution().executed()))
                .toCompletableFuture().join();
        assertTrue(repeat.execution().outcomes().isEmpty());
        assertEquals(2, batches.size());
        assertEquals(post.capabilities(), repeat.capabilities());
    }

    @Test
    void shouldPropagatePlanningFailureBeforeExecution() {
        IllegalStateException failure = new IllegalStateException("planning failed");
        CapabilityExecutionCommandService service = service(
                List.of(definition("first", ignored -> { throw failure; })),
                List.of(executor("first", units -> { throw new AssertionError("must not execute"); })));
        CompletionStage<CapabilityExecutionOutput> result = assertDoesNotThrow(() -> service.execute(command(Set.of())));
        assertSame(failure, assertThrows(CompletionException.class, () -> result.toCompletableFuture().join()).getCause());
    }

    @Test
    void shouldCopyCommandKeysAndRejectMissingInputs() {
        Set<CapabilityExecutionKey> keys = new HashSet<>();
        CapabilityExecutionKey key = new CapabilityExecutionKey("previous", "unit", null);
        keys.add(key);
        CapabilityExecutionCommand command = command(keys);
        keys.clear();
        assertEquals(Set.of(key), command.executed());
        assertThrows(UnsupportedOperationException.class, () -> command.executed().clear());
        assertThrows(NullPointerException.class, () -> new CapabilityExecutionCommand(null, BEFORE, Set.of()));
        assertThrows(NullPointerException.class, () -> new CapabilityExecutionCommand(CapabilityComposition.empty(), null, Set.of()));
        assertThrows(NullPointerException.class, () -> command(null));
    }

    private record Unit(String unitId) implements CapabilityUnit { }
    private record OtherUnit(String unitId) implements CapabilityUnit { }
    private record Outcome(boolean allowed) implements CapabilityOutcome { }

    private static CapabilityExecutionCommand command(Set<CapabilityExecutionKey> previous) {
        return new CapabilityExecutionCommand(CapabilityComposition.empty(), BEFORE, previous);
    }

    private static CapabilityExecutionCommandService service(List<CapabilityDefinition> definitions,
            List<CapabilityExecutor<?>> executors) {
        return new CapabilityExecutionCommandService(new CapabilityResolver(), new CapabilityPlanner(definitions), executors);
    }

    private static CapabilityExecutor<Unit> executor(String id, Function<List<Unit>, CompletionStage<Outcome>> action) {
        return new CapabilityExecutor<>() {
            public String capability() { return id; }
            public Class<Unit> unitType() { return Unit.class; }
            public CompletionStage<Outcome> execute(List<Unit> units) { return action.apply(units); }
        };
    }

    private static CapabilityDefinition definition(String id, String... units) {
        return definition(id, ignored -> java.util.Arrays.stream(units).map(Unit::new).toList());
    }

    private static CapabilityDefinition definition(String id,
            Function<ResolvedCapabilities, List<? extends CapabilityUnit>> units) {
        return new CapabilityDefinition() {
            public String capability() { return id; }
            public Set<CapabilitySlot> slots() { return Set.of(CapabilitySlot.PRE_AUTH, CapabilitySlot.POST_AUTH); }
            public CapabilityRepeat repeat() { return CapabilityRepeat.ONCE_PER_REQUEST; }
            public List<? extends CapabilityUnit> units(ResolvedCapabilities resolved, CapabilityContext context) {
                return units.apply(resolved);
            }
        };
    }
}
