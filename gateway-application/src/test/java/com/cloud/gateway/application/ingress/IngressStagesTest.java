package com.cloud.gateway.application.ingress;

import com.cloud.gateway.application.policy.VersionedPolicySnapshot;
import com.cloud.gateway.application.quota.RequestQuotaExecutor;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.access.IngressAccessEvaluator;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.SubjectAuthencationRequirement;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.policy.bundle.PolicyComposition;
import com.cloud.gateway.domain.policy.bundle.PolicyResolver;
import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
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
    private static final Endpoint ENDPOINT = new Endpoint(new EndpointId("read"), "service",
            List.of(new EndpointMatcher("GET", "/read")), "route");

    @Test
    void shouldCaptureOnceAndProtectUnknownEndpointsBeforeReturning() {
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger acquisitions = new AtomicInteger();
        IngressAccessSnapshot snapshot = snapshot();
        IngressPreparationCommandService preparation = new IngressPreparationCommandService(() -> {
            reads.incrementAndGet();
            return new VersionedPolicySnapshot(snapshot, "revision");
        }, (registry, method, path) -> List.of(), (groups, dimensions, ipOnly, applied) -> {
            assertTrue(ipOnly);
            acquisitions.incrementAndGet();
            applied.add("protected");
            return CompletableFuture.completedFuture(RateLimitDecision.allowed(null, null, 2));
        });
        IngressPreparationOutput output = preparation.prepare(new IngressPreparationCommand("GET", "/unknown", "ip"))
                .toCompletableFuture().join();
        assertSame(snapshot, output.snapshot());
        assertNull(output.endpoint());
        assertEquals(Set.of("protected"), output.appliedRules());
        assertEquals(1, reads.get());
        assertEquals(1, acquisitions.get());
    }

    @Test
    void shouldApplyGlobalProtectionBeforeRejectingAmbiguousCandidates() {
        AtomicInteger acquisitions = new AtomicInteger();
        Endpoint other = new Endpoint(new EndpointId("other"), "service",
                List.of(new EndpointMatcher("GET", "/**")), "route");
        IngressAccessSnapshot snapshot = snapshot();
        IngressPreparationCommandService preparation = new IngressPreparationCommandService(
                () -> new VersionedPolicySnapshot(snapshot, "revision"),
                (registry, method, path) -> List.of(ENDPOINT, other),
                (groups, dimensions, ipOnly, applied) -> {
                    assertTrue(ipOnly);
                    acquisitions.incrementAndGet();
                    return CompletableFuture.completedFuture(RateLimitDecision.allowed(null, null, 1));
                });
        java.util.concurrent.CompletionException failure = assertThrows(java.util.concurrent.CompletionException.class,
                () -> preparation.prepare(new IngressPreparationCommand("GET", "/read", "ip"))
                        .toCompletableFuture().join());
        assertInstanceOf(com.cloud.gateway.domain.common.DomainException.class, failure.getCause());
        assertEquals(1, acquisitions.get());
    }

    @Test
    void shouldAwaitAuthenticationThenAuthorizeBeforeAcquiringQuota() {
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<SubjectAuthenticationOutcome> pending = new CompletableFuture<>();
        IngressAdmissionCommandService service = admission((groups, dimensions, ipOnly, applied) -> {
            assertTrue(pending.isDone());
            assertFalse(ipOnly);
            assertEquals(Set.of("ip-rule"), applied);
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(RateLimitDecision.allowed(null, null, 1));
        });
        CompletableFuture<IngressAdmissionOutput> result = service.admit(command(APP), null, () -> pending,
                () -> { throw new AssertionError("browser must not run"); }, new HashSet<>(Set.of("ip-rule")))
                .toCompletableFuture();
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
                () -> CompletableFuture.completedFuture(SubjectAuthenticationOutcome.absent()),
                () -> { throw new AssertionError("browser must not run"); }, new HashSet<>())
                .toCompletableFuture().join();
        assertInstanceOf(IngressAccessOutput.Denied.class, result.access());
        assertNull(result.quota());
    }

    @Test
    void shouldCancelPendingSubjectWhenRequestIsCancelled() {
        CompletableFuture<SubjectAuthenticationOutcome> subject = new CompletableFuture<>();
        CompletableFuture<IngressAdmissionOutput> request = admission((groups, dimensions, ipOnly, applied) -> {
            throw new AssertionError("cancelled request must not acquire quota");
        }).admit(command(APP), null, () -> subject, () -> subject, new HashSet<>()).toCompletableFuture();
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
                (credentials, evidence, now) -> CompletableFuture.completedFuture(ClientVerificationOutcome.absent()),
                new IngressAccessEvaluator(), new PolicyResolver(), Clock.fixed(NOW, ZoneOffset.UTC)), quotas);
    }

    private static IngressAccessCommand command(AppId app) {
        return new IngressAccessCommand(snapshot(), ENDPOINT, app, null, null, false, "ip");
    }

    private static IngressAccessSnapshot snapshot() {
        EndpointPolicy access = new EndpointPolicy(ENDPOINT.endpointId(), ClientVerificationRequirement.OPTIONAL,
                ClientVerificationMode.IGNORE, SubjectAuthencationRequirement.HOST_SESSION_OPTIONAL, Set.of(), Set.of("api"),
                CredentialForwarding.stripAll(), EndpointStatus.ENABLED);
        return new IngressAccessSnapshot(1, new IngressAccessContent(new EndpointRegistry(List.of(ENDPOINT)),
                Map.of(APP, new AppPolicy(APP, "web", Set.of(), PolicyStatus.ENABLED)), Map.of(),
                Map.of(ENDPOINT.endpointId(), access), new PolicyComposition(Map.of(), List.of())), "hash", NOW);
    }
}
