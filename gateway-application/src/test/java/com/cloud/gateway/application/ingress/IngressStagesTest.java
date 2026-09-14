package com.cloud.gateway.application.ingress;

import com.cloud.gateway.application.access.publication.VersionedAccessSnapshot;
import com.cloud.gateway.application.quota.RequestQuotaExecutor;
import com.cloud.gateway.domain.access.AccessSnapshotVersion;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.access.RequestEvidence;
import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppAccess;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.access.IngressAccessEvaluator;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationRejectReason;
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
import com.cloud.gateway.domain.policy.bundle.PolicyConfig;
import com.cloud.gateway.domain.policy.bundle.PolicyResolver;
import com.cloud.gateway.domain.policy.capability.QuotaExecution;
import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
import com.cloud.gateway.domain.policy.capability.RateLimitRuleId;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.client.ClientRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
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
                    SubjectAuthenticationRequirement.OPTIONAL, Set.of(), Set.of("api")));

    @Test
    void shouldCaptureOnceAndProtectUnknownEndpointsBeforeReturning() {
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger acquisitions = new AtomicInteger();
        IngressAccessSnapshot snapshot = snapshot();
        IngressPreparationCommandService preparation = new IngressPreparationCommandService(() -> {
            reads.incrementAndGet();
            return new com.cloud.gateway.application.access.publication.VersionedAccessSnapshot(snapshot, "revision");
        }, (registry, method, path) -> List.of(), (groups, dimensions, ipOnly, applied) -> {
            assertTrue(ipOnly);
            acquisitions.incrementAndGet();
            applied.add(new RateLimitRuleId("protected"));
            return CompletableFuture.completedFuture(RateLimitDecision.allowed(null, null, 2));
        }, () -> { throw new AssertionError("unknown path must not be reported as ambiguous"); });
        IngressPreparationOutput output = preparation.prepare(new PrepareIngressCommand(
                new RequestEvidence("GET", "/unknown", null, APP, null, null, "ip")))
                .toCompletableFuture().join();
        IngressPreparationOutput.Prepared prepared = assertInstanceOf(IngressPreparationOutput.Prepared.class, output);
        assertSame(snapshot, prepared.snapshot());
        assertInstanceOf(EndpointMatch.None.class, prepared.endpointMatch());
        assertTrue(prepared.quota().executed(new RateLimitRuleId("protected")));
        assertEquals(1, reads.get());
        assertEquals(1, acquisitions.get());
    }

    @Test
    void shouldProtectAmbiguousPathsWithGlobalQuotaWithoutRejectingThem() {
        AtomicInteger acquisitions = new AtomicInteger();
        Endpoint other = new Endpoint(new EndpointId("other"), new ServiceId("service"), new RouteId("route"),
                List.of(new EndpointMatcher("GET", "/**")), Set.of(), EndpointStatus.ENABLED,
                new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                        SubjectAuthenticationRequirement.OPTIONAL, Set.of(), Set.of()));
        IngressAccessSnapshot snapshot = snapshot();
        AtomicInteger ambiguities = new AtomicInteger();
        IngressPreparationCommandService preparation = new IngressPreparationCommandService(
                () -> new com.cloud.gateway.application.access.publication.VersionedAccessSnapshot(snapshot, "revision"),
                (registry, method, path) -> List.of(ENDPOINT, other),
                (groups, dimensions, ipOnly, applied) -> {
                    assertTrue(ipOnly);
                    acquisitions.incrementAndGet();
                    return CompletableFuture.completedFuture(RateLimitDecision.allowed(null, null, 1));
                }, () -> ambiguities.incrementAndGet());
        IngressPreparationOutput output = preparation.prepare(new PrepareIngressCommand(
                new RequestEvidence("GET", "/read", null, APP, null, null, "ip")))
                .toCompletableFuture().join();
        IngressPreparationOutput.Prepared prepared = assertInstanceOf(IngressPreparationOutput.Prepared.class, output);
        assertInstanceOf(EndpointMatch.Ambiguous.class, prepared.endpointMatch());
        assertEquals(1, acquisitions.get());
        assertEquals(1, ambiguities.get());
    }

    @Test
    void shouldAwaitAuthenticationThenAuthorizeBeforeAcquiringQuota() {
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<SubjectAuthenticationOutcome> pending = new CompletableFuture<>();
        IngressAdmissionCommandService service = admission((groups, dimensions, ipOnly, applied) -> {
            assertTrue(pending.isDone());
            assertFalse(ipOnly);
            assertEquals(Set.of(new RateLimitRuleId("ip-rule")), applied);
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(RateLimitDecision.allowed(null, null, 1));
        });
        CompletableFuture<IngressAdmissionOutput> result = service.admit(command(APP), null, () -> pending,
                new HashSet<>(Set.of(new RateLimitRuleId("ip-rule")))).toCompletableFuture();
        assertFalse(result.isDone());
        assertEquals(0, calls.get());
        pending.complete(SubjectAuthenticationOutcome.absent());
        assertInstanceOf(IngressAccessOutput.Allowed.class, result.join().access());
        assertEquals(1, calls.get());
    }

    @Test
    void shouldNeverAcquireQuotaForDeniedAccess() {
        IngressAdmissionCommandService service = admission((groups, dimensions, ipOnly, applied) -> {
            throw new AssertionError("denied access must not acquire quota");
        });
        IngressAdmissionOutput result = service.admit(command(null), null,
                () -> CompletableFuture.completedFuture(SubjectAuthenticationOutcome.absent()), new HashSet<>())
                .toCompletableFuture().join();
        assertInstanceOf(IngressAccessOutput.Denied.class, result.access());
        assertNull(result.quota());
    }

    @Test
    void shouldCancelPendingSubjectWhenRequestIsCancelled() {
        CompletableFuture<SubjectAuthenticationOutcome> subject = new CompletableFuture<>();
        CompletableFuture<IngressAdmissionOutput> request = admission((groups, dimensions, ipOnly, applied) -> {
            throw new AssertionError("cancelled request must not acquire quota");
        }).admit(command(APP), null, () -> subject, new HashSet<>()).toCompletableFuture();
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

    private static IngressAdmissionCommandService admission(RequestQuotaExecutor quotas) {
        return new IngressAdmissionCommandService(new IngressAccessCommandService(
                (credentials, clientId, evidence, now) ->
                        CompletableFuture.completedFuture(ClientVerificationOutcome.absent()),
                new IngressAccessEvaluator(), new PolicyResolver(), Clock.fixed(NOW, ZoneOffset.UTC)), quotas);
    }

    private static IngressAccessCommand command(AppId app) {
        return new IngressAccessCommand(snapshot(), EndpointMatch.of(List.of(ENDPOINT)),
                new RequestEvidence("GET", "/read", null, app, null, null, "ip"));
    }

    private static IngressAccessSnapshot snapshot() {
        return new IngressAccessSnapshot(new AccessSnapshotVersion(1),
                new IngressAccessContent(
                        new AppRegistry(List.of(new App(APP, "web", AppStatus.ENABLED, new AppAccess(Set.of())))),
                        new ClientRegistry(List.of(new Client(new ClientId("client"), ClientStatus.ENABLED,
                                new ClientAccess(Set.of(APP), null)))),
                        new EndpointRegistry(List.of(ENDPOINT)),
                        new PolicyConfig(Map.of(), List.of())),
                "hash", NOW);
    }
}
