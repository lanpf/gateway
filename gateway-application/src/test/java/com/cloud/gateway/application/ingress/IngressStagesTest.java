package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.capability.configuration.EffectiveCapabilityConfig;

import com.cloud.gateway.application.ingress.CapabilityRegistration;

import com.cloud.gateway.domain.capability.configuration.CapabilityBinding;
import com.cloud.gateway.domain.capability.configuration.CapabilityBundle;
import com.cloud.gateway.domain.capability.configuration.CapabilityBindingId;
import com.cloud.gateway.domain.capability.configuration.CapabilityBundleId;

import com.cloud.gateway.application.configuration.publication.RevisionedAccessSnapshot;
import com.cloud.gateway.application.quota.RequestQuotaExecutor;
import com.cloud.gateway.domain.capability.runtime.CapabilityExecution;
import com.cloud.gateway.domain.capability.runtime.CapabilityExecutionKey;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDimension;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitMode;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitPolicy;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;
import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppAccess;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.ingress.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.ingress.access.AccessDecision;
import com.cloud.gateway.domain.ingress.access.AccessEvaluator;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import com.cloud.gateway.domain.ingress.access.VerifiedApp;
import com.cloud.gateway.domain.ingress.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatch;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.capability.configuration.CapabilityComposition;
import com.cloud.gateway.domain.capability.configuration.CapabilityResolver;
import com.cloud.gateway.domain.capability.runtime.RateLimitCapability;
import com.cloud.gateway.domain.capability.configuration.*;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRuleId;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.client.ClientRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IngressStagesTest {
    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");
    private static final AppId APP = new AppId("app");
    private static final Endpoint ENDPOINT = new Endpoint(new EndpointId("read"), new ServiceId("service"),
            new RouteId("route"), List.of(new EndpointMatcher("GET", "/read")), Set.of(), EndpointStatus.ENABLED,
            new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                    SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of("api")));

    @Test
    void shouldCaptureOnceAndProtectUnknownEndpointsBeforeReturning() {
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger acquisitions = new AtomicInteger();
        IngressSnapshot snapshot = snapshot();
        PreparationStageCommandService preparation = new PreparationStageCommandService(() -> {
            reads.incrementAndGet();
            return new RevisionedAccessSnapshot(snapshot,
                    new com.cloud.gateway.domain.ingress.snapshot.Revision("value"));
        }, (registry, method, path) -> List.of(), execution(planned -> {
            assertEquals(List.of(RateLimitDimension.IP), planned.stream().map(q -> q.rule().dimension()).toList());
            acquisitions.incrementAndGet();
            return CompletableFuture.completedFuture(RateLimitDecision.allowed(null, null, 2));
        }), () -> { throw new AssertionError("unknown path must not be reported as ambiguous"); });
        PreparationStageOutput output = preparation.prepare(new PreparationStageCommand(
                new RequestEvidence("GET", "/unknown", null, APP, null, null, "ip")))
                .toCompletableFuture().join();
        PreparationStageOutput.Prepared prepared = assertInstanceOf(PreparationStageOutput.Prepared.class, output);
        assertSame(snapshot, prepared.snapshot());
        assertInstanceOf(EndpointMatch.None.class, prepared.endpointMatch());
        assertTrue(prepared.execution().executed().contains(new CapabilityExecutionKey(RateLimitCapability.ID, "ip-rule", null)));
        assertEquals(1, reads.get());
        assertEquals(1, acquisitions.get());
    }

    @Test
    void shouldProtectAmbiguousPathsWithGlobalQuotaWithoutRejectingThem() {
        AtomicInteger acquisitions = new AtomicInteger();
        Endpoint other = new Endpoint(new EndpointId("other"), new ServiceId("service"), new RouteId("route"),
                List.of(new EndpointMatcher("GET", "/**")), Set.of(), EndpointStatus.ENABLED,
                new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                        SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of()));
        IngressSnapshot snapshot = snapshot();
        AtomicInteger ambiguities = new AtomicInteger();
        PreparationStageCommandService preparation = new PreparationStageCommandService(
                () -> new RevisionedAccessSnapshot(snapshot,
                        new com.cloud.gateway.domain.ingress.snapshot.Revision("value")),
                (registry, method, path) -> List.of(ENDPOINT, other),
                execution(planned -> {
                    assertEquals(List.of(RateLimitDimension.IP), planned.stream().map(q -> q.rule().dimension()).toList());
                    acquisitions.incrementAndGet();
                    return CompletableFuture.completedFuture(RateLimitDecision.allowed(null, null, 1));
                }), () -> ambiguities.incrementAndGet());
        PreparationStageOutput output = preparation.prepare(new PreparationStageCommand(
                new RequestEvidence("GET", "/read", null, APP, null, null, "ip")))
                .toCompletableFuture().join();
        PreparationStageOutput.Prepared prepared = assertInstanceOf(PreparationStageOutput.Prepared.class, output);
        assertInstanceOf(EndpointMatch.Ambiguous.class, prepared.endpointMatch());
        assertEquals(1, acquisitions.get());
        assertEquals(1, ambiguities.get());
    }

    @Test
    void shouldCancelPendingCapabilityThroughPreparation() {
        CompletableFuture<RateLimitDecision> quota = new CompletableFuture<>();
        PreparationStageCommandService preparation = new PreparationStageCommandService(
                () -> new RevisionedAccessSnapshot(snapshot(),
                        new com.cloud.gateway.domain.ingress.snapshot.Revision("value")),
                (registry, method, path) -> List.of(ENDPOINT), execution(planned -> quota), () -> { });
        CompletableFuture<PreparationStageOutput> request = preparation.prepare(new PreparationStageCommand(
                new RequestEvidence("GET", "/read", null, APP, null, null, "ip"))).toCompletableFuture();
        assertFalse(request.isDone());
        request.cancel(true);
        assertTrue(quota.isCancelled());
    }

    @Test
    void shouldAwaitAuthenticationThenAuthorizeBeforeAcquiringQuota() {
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<SubjectAuthenticationOutcome> pending = new CompletableFuture<>();
        IngressAdmissionCommandService service = ingress(planned -> {
            assertTrue(pending.isDone());
            assertEquals(List.of("app-rule"), planned.stream().map(q -> q.rule().ruleId().value()).toList());
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(RateLimitDecision.allowed(null, null, 1));
        });
        CompletableFuture<IngressAdmissionOutput> result = service.admit(
                command(APP, Set.of(new RateLimitRuleId("ip-rule"))), () -> pending).toCompletableFuture();
        assertFalse(result.isDone());
        assertEquals(0, calls.get());
        pending.complete(SubjectAuthenticationOutcome.absent());
        assertInstanceOf(IngressAdmissionOutput.Evaluated.class, result.join());
        assertEquals(1, calls.get());
    }

    @Test
    void shouldAdmitTheAccessOutputWithoutRunningTheAccessStage() {
        AtomicInteger acquisitions = new AtomicInteger();
        AdmissionStageCommandService service = admission(planned -> {
            assertEquals(List.of("app-rule"), planned.stream().map(q -> q.rule().ruleId().value()).toList());
            acquisitions.incrementAndGet();
            return CompletableFuture.completedFuture(RateLimitDecision.allowed(null, null, 1));
        });
        AccessStageOutput.Allowed allowed = new AccessStageOutput.Allowed(
                new AccessDecision.Allowed(new AccessGrantedContext(new VerifiedApp(APP, "web"), null, null),
                        new SnapshotVersion(1)),
                command(APP, Set.of(new RateLimitRuleId("ip-rule"))).prepared());

        AdmissionStageOutput result = service.admit(new AdmissionStageCommand(allowed)).toCompletableFuture().join();

        assertInstanceOf(AccessStageOutput.Allowed.class, result.access());
        assertTrue(result.execution().continues());
        assertNotNull(result.capabilities().get(com.cloud.gateway.domain.capability.header.HeaderConfigDefinition.KEY));
        assertEquals(1, acquisitions.get());
    }

    @Test
    void shouldNeverAcquireQuotaForDeniedAccess() {
        IngressAdmissionCommandService service = ingress(planned -> {
            throw new AssertionError("denied access must not acquire quota");
        });
        IngressAdmissionOutput result = service.admit(command(null, Set.of()),
                () -> CompletableFuture.completedFuture(SubjectAuthenticationOutcome.absent()))
                .toCompletableFuture().join();
        IngressAdmissionOutput.AccessDenied denied = assertInstanceOf(IngressAdmissionOutput.AccessDenied.class, result);
        assertEquals(com.cloud.gateway.domain.ingress.access.AccessDenyReason.APP_NOT_FOUND,
                denied.access().decision().denyReason());
    }

    @Test
    void shouldCancelPendingSubjectWhenRequestIsCancelled() {
        CompletableFuture<SubjectAuthenticationOutcome> subject = new CompletableFuture<>();
        CompletableFuture<IngressAdmissionOutput> request = ingress(planned -> {
            throw new AssertionError("cancelled request must not acquire quota");
        }).admit(command(APP, Set.of()), () -> subject).toCompletableFuture();
        request.cancel(true);
        assertTrue(subject.isCancelled());
    }

    @Test
    void shouldCancelSiblingOnFailureAndRejectLateWork() {
        IngressTask<String> task = new IngressTask<>();
        CompletableFuture<String> first = new CompletableFuture<>();
        CompletableFuture<String> second = new CompletableFuture<>();
        task.track(first);
        task.track(second);
        first.completeExceptionally(new IllegalStateException("dependency failed"));
        assertTrue(task.isCompletedExceptionally());
        assertTrue(second.isCancelled());
        CompletableFuture<String> late = new CompletableFuture<>();
        task.track(late);
        assertTrue(late.isCancelled());
    }

    @Test
    void shouldCancelPendingQuotaThroughBothOrchestrationAndAdmission() {
        CompletableFuture<RateLimitDecision> quota = new CompletableFuture<>();
        CompletableFuture<IngressAdmissionOutput> request = ingress(planned -> quota)
                .admit(command(APP, Set.of()),
                        () -> CompletableFuture.completedFuture(SubjectAuthenticationOutcome.absent()))
                .toCompletableFuture();
        assertFalse(request.isDone());
        request.cancel(true);
        assertTrue(quota.isCancelled());
    }

    @Test
    void shouldPropagateAuthenticationFailureWithoutRunningAdmission() {
        IllegalStateException failure = new IllegalStateException("authentication unavailable");
        CompletableFuture<IngressAdmissionOutput> request = ingress(planned -> {
            throw new AssertionError("failed authentication must not start admission");
        }).admit(command(APP, Set.of()), () -> CompletableFuture.failedFuture(failure)).toCompletableFuture();
        assertSame(failure, assertThrows(java.util.concurrent.CompletionException.class, request::join).getCause());
    }

    @Test
    void shouldPropagateQuotaFailureAndKeepQuotaRejectionDistinctFromAccessDenial() {
        IllegalStateException failure = new IllegalStateException("quota failed");
        CompletableFuture<IngressAdmissionOutput> request = ingress(planned -> {
            throw failure;
        }).admit(command(APP, Set.of()),
                () -> CompletableFuture.completedFuture(SubjectAuthenticationOutcome.absent())).toCompletableFuture();
        assertSame(failure, assertThrows(java.util.concurrent.CompletionException.class, request::join).getCause());

        RateLimitDecision rejection = RateLimitDecision.rejected(null, null, java.time.Duration.ofSeconds(2));
        AccessStageCommand command = command(APP, Set.of());
        IngressAdmissionOutput output = ingress(planned ->
                CompletableFuture.completedFuture(rejection)).admit(command,
                () -> CompletableFuture.completedFuture(SubjectAuthenticationOutcome.absent()))
                .toCompletableFuture().join();
        AdmissionStageOutput evaluated = assertInstanceOf(IngressAdmissionOutput.Evaluated.class, output).admission();
        assertSame(rejection, ((RateLimitCapability.Outcome) evaluated.execution().outcomes().get(0)).decision());
        assertSame(command.prepared(), evaluated.access().prepared());
    }

    private static AdmissionStageCommandService admission(RequestQuotaExecutor quotas) {
        return new AdmissionStageCommandService(execution(quotas));
    }

    private static CapabilityExecutionCommandService execution(RequestQuotaExecutor quotas) {
        return new CapabilityExecutionCommandService(new CapabilityResolver(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry()), planner(),
                List.of(new RateLimitCapabilityExecutor(quotas)));
    }

    private static List<CapabilityRegistration<?>> planner() {
        return List.of(new CapabilityRegistration<>(new RateLimitCapability(), config -> config.get(com.cloud.gateway.domain.capability.ratelimit.RateLimitConfigDefinition.KEY)));
    }

    private static IngressAdmissionCommandService ingress(RequestQuotaExecutor quotas) {
        return new IngressAdmissionCommandService(new AccessStageCommandService(
                (credentials, clientId, evidence, now) ->
                        CompletableFuture.completedFuture(ClientVerificationOutcome.absent()),
                new AccessEvaluator(), Clock.fixed(NOW, ZoneOffset.UTC)), admission(quotas));
    }

    private static AccessStageCommand command(AppId app, Set<RateLimitRuleId> applied) {
        return new AccessStageCommand(new PreparationStageOutput.Prepared(snapshot(),
                EndpointMatch.of(List.of(ENDPOINT)),
                new RequestEvidence("GET", "/read", null, app, null, null, "ip"),
                new CapabilityExecution(applied.stream().map(id -> new CapabilityExecutionKey(
                        RateLimitCapability.ID, id.value(), null)).collect(java.util.stream.Collectors.toSet()), List.of())), null);
    }

    private static IngressSnapshot snapshot() {
        return new IngressSnapshot(new SnapshotVersion(1),
                new IngressContent(
                        new AppRegistry(List.of(new App(APP, "web", AppStatus.ENABLED, new AppAccess(Set.of())))),
                        new ClientRegistry(List.of(new Client(new ClientId("client"), ClientStatus.ENABLED,
                                new ClientAccess(Set.of(APP), null)))),
                        new EndpointRegistry(List.of(ENDPOINT)),
                        composition()),
                "hash", NOW);
    }

    private static CapabilityComposition composition() {
        CapabilityBundleId id = new CapabilityBundleId("limits");
        CapabilityBundle bundle = new CapabilityBundle(id, java.util.List.of(new RateLimitPolicy(List.of(
                new RateLimitRule("ip-rule", RateLimitDimension.IP, 10, 1, RateLimitMode.EXACT),
                new RateLimitRule("app-rule", RateLimitDimension.APP, 10, 1, RateLimitMode.EXACT)), null)));
        return new CapabilityComposition(Map.of(id, bundle), List.of(new CapabilityBinding(
                new CapabilityBindingId("global"), id, null, null, Set.of(), null, null, null)));
    }
}
