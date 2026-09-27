package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.capability.configuration.CapabilityBinding;
import com.cloud.gateway.domain.capability.configuration.CapabilityBundle;
import com.cloud.gateway.domain.capability.configuration.CapabilityComposition;
import com.cloud.gateway.domain.capability.configuration.CapabilityResolver;
import com.cloud.gateway.domain.capability.configuration.CapabilityBindingId;
import com.cloud.gateway.domain.capability.configuration.CapabilityBundleId;

import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.capability.runtime.PlannableCapability;
import com.cloud.gateway.application.ingress.CapabilityRegistration;
import com.cloud.gateway.domain.capability.runtime.CapabilityContext;
import com.cloud.gateway.domain.capability.runtime.CapabilityExecution;
import com.cloud.gateway.domain.capability.runtime.CapabilityExecutionKey;
import com.cloud.gateway.domain.capability.runtime.CapabilityOutcome;
import com.cloud.gateway.domain.capability.runtime.CapabilityRepeat;
import com.cloud.gateway.domain.capability.runtime.CapabilitySlot;
import com.cloud.gateway.domain.capability.runtime.CapabilityUnit;
import com.cloud.gateway.domain.capability.runtime.RateLimitCapability;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDimension;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitMode;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitPolicy;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;
import com.cloud.gateway.domain.endpoint.*;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import com.cloud.gateway.domain.ingress.access.VerifiedApp;
import com.cloud.gateway.domain.capability.configuration.*;
import com.cloud.gateway.domain.capability.header.HeaderPolicy;
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
                        executor("first", units -> { calls.add("first"); return CompletableFuture.completedFuture(new Outcome(CapabilityOutcome.Disposition.CONTINUE)); }),
                        executor("second", units -> { calls.add("second"); assertEquals(2, units.size()); return CompletableFuture.completedFuture(new Outcome(CapabilityOutcome.Disposition.STOP)); }),
                        executor("third", units -> { throw new AssertionError("must short circuit"); })));
        CapabilityExecutionKey previous = new CapabilityExecutionKey("previous", "unit", null);
        CapabilityExecutionOutput output = service.execute(command(Set.of(previous))).toCompletableFuture().join();
        CapabilityExecution report = output.execution();
        assertEquals(List.of("first", "second"), calls);
        assertFalse(report.continues());
        assertEquals(4, report.executed().size());
        assertEquals(2, report.outcomes().size());
        assertTrue(report.executed().contains(previous));
        assertNotNull(output.capabilities().get(com.cloud.gateway.domain.capability.header.HeaderConfigDefinition.KEY));
    }

    @Test
    void shouldValidateAllExecutorsBeforeAnySideEffect() {
        CapabilityExecutor<Unit> first = executor("first", units -> {
            throw new AssertionError("validation must precede execution");
        });
        assertThrows(IllegalArgumentException.class, () -> service(
                List.of(definition("first", "one"), definition("missing", "two")), List.of(first)));
        CapabilityRegistration<Object> wrongType = definition("first", ignored -> List.of(new OtherUnit("bad")));
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
        assertTrue(service(List.of(), List.of()).execute(command(Set.of())).toCompletableFuture().join().execution().continues());
    }

    @Test
    void shouldHandThePlannerTheSameResolvedNodeTheOutputReports() {
        AtomicReference<List<RateLimitPolicy>> planned = new AtomicReference<>();
        CapabilityRegistration<List<RateLimitPolicy>> definition = definition(RateLimitCapability.ID, node -> {
            planned.set(node);
            return List.of(new Unit("unit"));
        });
        HeaderPolicy headers = new HeaderPolicy(Set.of(), Map.of("X-Gateway-Test", "selected"), Set.of(), null);
        CapabilityBundleId id = new CapabilityBundleId("header");
        CapabilityComposition composition = new CapabilityComposition(
                Map.of(id, new CapabilityBundle(id, java.util.List.of(headers))),
                List.of(new CapabilityBinding(new CapabilityBindingId("global"), id, null, null, Set.of(), null, null, null)));
        CapabilityExecutionCommandService service = service(List.of(
                new CapabilityRegistration<>(definition.capability(), config -> config.get(com.cloud.gateway.domain.capability.ratelimit.RateLimitConfigDefinition.KEY))),
                List.of(executor(RateLimitCapability.ID, units -> CompletableFuture.completedFuture(new Outcome(CapabilityOutcome.Disposition.STOP)))));
        CapabilityExecutionOutput output = service.execute(new CapabilityExecutionCommand(composition, BEFORE, Set.of()))
                .toCompletableFuture().join();
        assertSame(planned.get(), output.capabilities().get(com.cloud.gateway.domain.capability.ratelimit.RateLimitConfigDefinition.KEY));
        assertSame(headers, output.capabilities().get(com.cloud.gateway.domain.capability.header.HeaderConfigDefinition.KEY));
        assertFalse(output.execution().continues());
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
        CapabilityComposition composition = new CapabilityComposition(Map.of(id, new CapabilityBundle(id, java.util.List.of(new RateLimitPolicy(List.of(
                        new RateLimitRule("ip", RateLimitDimension.IP, 10, 1, RateLimitMode.EXACT),
                        new RateLimitRule("app", RateLimitDimension.APP, 10, 1, RateLimitMode.EXACT)), null)))), List.of(
                new CapabilityBinding(new CapabilityBindingId("global"), id, null, null, Set.of(), null, null, null)));
        List<List<String>> batches = new ArrayList<>();
        CapabilityExecutionCommandService service = service(List.of(
                new CapabilityRegistration<>(new RateLimitCapability(), config -> config.get(com.cloud.gateway.domain.capability.ratelimit.RateLimitConfigDefinition.KEY))), List.of(
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
    private record Outcome(CapabilityOutcome.Disposition disposition) implements CapabilityOutcome {
    }

    private static CapabilityExecutionCommand command(Set<CapabilityExecutionKey> previous) {
        return new CapabilityExecutionCommand(CapabilityComposition.empty(), BEFORE, previous);
    }

    private static CapabilityExecutionCommandService service(List<CapabilityRegistration<?>> definitions,
            List<CapabilityExecutor<?>> executors) {
        return new CapabilityExecutionCommandService(new CapabilityResolver(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry()), definitions, executors);
    }

    private static CapabilityExecutor<Unit> executor(String id, Function<List<Unit>, CompletionStage<Outcome>> action) {
        return new CapabilityExecutor<>() {
            public String capability() { return id; }
            public Class<Unit> unitType() { return Unit.class; }
            public CompletionStage<Outcome> execute(List<Unit> units) { return action.apply(units); }
        };
    }

    private static CapabilityRegistration<Object> definition(String id, String... units) {
        return definition(id, ignored -> java.util.Arrays.stream(units).map(Unit::new).toList());
    }

    private static <N> CapabilityRegistration<N> definition(String id,
            Function<N, List<? extends CapabilityUnit>> units) {
        return new CapabilityRegistration<>(new PlannableCapability<N>() {
            public String name() { return id; }
            public Set<CapabilitySlot> slots() { return Set.of(CapabilitySlot.PRE_AUTH, CapabilitySlot.POST_AUTH); }
            public CapabilityRepeat repeat() { return CapabilityRepeat.ONCE_PER_REQUEST; }
            public List<? extends CapabilityUnit> units(N node, CapabilityContext context) {
                return units.apply(node);
            }
        }, config -> null);
    }
}
