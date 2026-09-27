package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.capability.configuration.EffectiveCapabilityPolicies;

import com.cloud.gateway.application.ingress.CapabilityRegistration;

import com.cloud.gateway.domain.capability.configuration.CapabilityBinding;
import com.cloud.gateway.domain.capability.configuration.CapabilityBundle;
import com.cloud.gateway.domain.capability.configuration.CapabilityComposition;
import com.cloud.gateway.domain.capability.configuration.CapabilityPolicyAssembler;
import com.cloud.gateway.domain.capability.configuration.CapabilityBindingId;
import com.cloud.gateway.domain.capability.configuration.CapabilityBundleId;

import static org.junit.jupiter.api.Assertions.*;

import com.cloud.gateway.application.security.ClientCredentialAuthenticator;
import com.cloud.gateway.application.security.ClientSignatureEvidence;
import com.cloud.framework.core.SubjectType;
import com.cloud.gateway.domain.capability.runtime.CapabilityContext;
import com.cloud.gateway.domain.capability.runtime.CapabilityExecution;
import com.cloud.gateway.domain.capability.runtime.CapabilitySlot;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitMode;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitConfig;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;
import com.cloud.gateway.domain.capability.header.CredentialForwarding;
import com.cloud.gateway.domain.ingress.access.*;
import com.cloud.gateway.domain.ingress.snapshot.*;
import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppAccess;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.ingress.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.ingress.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.ingress.access.client.VerifiedClient;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointMatch;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.capability.configuration.*;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.ingress.access.subject.AuthenticatedSubject;
import com.cloud.gateway.domain.ingress.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.capability.runtime.RateLimitCapability;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDimension;
import com.cloud.gateway.domain.capability.header.HeaderConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class AccessStageCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");
    private static final AppId APP = new AppId("app");
    private static final ClientId CLIENT = new ClientId("client");
    private static final CredentialId CREDENTIAL = new CredentialId("client");
    private static final Endpoint ENDPOINT = new Endpoint(new EndpointId("TEST.GET"),
            new com.cloud.gateway.domain.endpoint.ServiceId("service"),
            new com.cloud.gateway.domain.endpoint.RouteId("route"),
            List.of(new EndpointMatcher("GET", "/test")), Set.of(), EndpointStatus.ENABLED,
            new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                    SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of()));
    private static final HeaderConfig CLIENT_HEADERS = new HeaderConfig(Set.of(),
            Map.of("X-Gateway-Test", "verified"), Set.of(), CredentialForwarding.stripAll());

    @Test
    void shouldNotPromoteDeclaredClientToContextBindingOrQuotaDimension() {
        // Declared client, but no signature submitted: verification is Absent, so nothing is promoted.
        AccessStageOutput.Allowed allowed = assertInstanceOf(AccessStageOutput.Allowed.class,
                evaluate(command(true, APP, null), absentSubject()));
        assertNull(allowed.decision().grantedContext().client());
        AdmissionStageOutput admitted = admit(allowed, Map.of(RateLimitDimension.IP, "192.0.2.1",
                RateLimitDimension.APP, "app", RateLimitDimension.ENDPOINT, "TEST.GET"));
        assertEquals(HeaderConfig.defaults(CredentialForwarding.stripAll()),
                admitted.capabilities().get(com.cloud.gateway.domain.capability.header.HeaderPolicyComposer.INSTANCE).config());
    }

    @Test
    void shouldResolveVerifiedClientBindingAndSubjectDimensionAfterAuthorization() {
        SubjectAuthenticationOutcome subject = SubjectAuthenticationOutcome.authenticated(new AuthenticatedSubject(
                SubjectType.HOST_SESSION, new com.cloud.gateway.domain.ingress.access.subject.SubjectId("user"),
                new com.cloud.gateway.domain.ingress.access.subject.SessionId("session"), "issuer", Set.of("api"), Set.of(),
                NOW.plusSeconds(60)));
        AccessStageOutput.Allowed allowed = assertInstanceOf(AccessStageOutput.Allowed.class,
                evaluate(service(verifiedAuthenticator()), command(true, APP, signedEvidence()), subject(subject)));
        assertEquals(CLIENT, allowed.decision().grantedContext().client().clientId());
        AdmissionStageOutput admitted = admit(allowed, Map.of(RateLimitDimension.IP, "192.0.2.1",
                RateLimitDimension.APP, "app", RateLimitDimension.ENDPOINT, "TEST.GET",
                RateLimitDimension.CLIENT, "client", RateLimitDimension.SUBJECT, "HOST_SESSION:user"));
        assertEquals(CLIENT_HEADERS, admitted.capabilities().get(com.cloud.gateway.domain.capability.header.HeaderPolicyComposer.INSTANCE).config());
    }

    @Test
    void shouldReturnDomainDenialWithCapturedVersionWithoutExecutablePolicy() {
        AccessStageOutput.Denied denied = assertInstanceOf(AccessStageOutput.Denied.class,
                evaluate(command(true, null, signedEvidence()), () -> {
                    throw new AssertionError("missing app must deny before authentication");
                }));
        assertEquals(AccessDenyReason.APP_NOT_FOUND, denied.decision().denyReason());
        assertEquals(7, denied.decision().snapshotVersion().value());
    }

    @Test
    void shouldDenyAmbiguousCandidatesWithoutJoiningAuthentication() {
        AccessStageOutput.Denied denied = assertInstanceOf(AccessStageOutput.Denied.class,
                evaluate(ambiguousCommand(), () -> {
                    throw new AssertionError("ambiguity must deny without joining authentication");
                }));
        assertEquals(AccessDenyReason.ENDPOINT_NOT_FOUND, denied.decision().denyReason());
    }

    @Test
    void shouldUseCapturedCredentialsOriginalEvidenceAndInjectedTime() {
        ClientSignatureEvidence evidence = new ClientSignatureEvidence(new byte[]{1, 2}, "123", "signature",
                "GET", "/a%2Fb", "x=1&x=2", "app", "client", "client", "nonce");
        AccessStageCommand command = command(true, APP, evidence);
        AtomicInteger calls = new AtomicInteger();
        ClientCredentialAuthenticator authenticator = (credentials, clientId, original, now) -> {
            calls.incrementAndGet();
            assertSame(command.prepared().snapshot().content().clients().find(CLIENT).orElseThrow()
                    .access().credentials(), credentials);
            assertSame(evidence, original);
            assertEquals(CLIENT, clientId);
            assertEquals(NOW, now);
            return CompletableFuture.completedFuture(verified());
        };
        assertInstanceOf(AccessStageOutput.Allowed.class,
                evaluate(service(authenticator), command, absentSubject()));
        assertEquals(1, calls.get());
    }

    @Test
    void shouldNotAttemptVerificationWithoutCredentialsOrEvidence() {
        AccessStageCommandService service = service(unusedAuthenticator());
        // No configured credentials, no submitted evidence: Absent, and OPTIONAL lets it through.
        assertInstanceOf(AccessStageOutput.Allowed.class,
                evaluate(service, command(false, APP, null), absentSubject()));
        // Configured credentials but no submitted evidence: still Absent.
        assertInstanceOf(AccessStageOutput.Allowed.class,
                evaluate(service, command(true, APP, null), absentSubject()));
        // Submitted evidence without configured credentials: Rejected, never silently ignored.
        AccessStageOutput.Denied denied = assertInstanceOf(AccessStageOutput.Denied.class,
                evaluate(service, command(false, APP, signedEvidence()), absentSubject()));
        assertEquals(AccessDenyReason.CLIENT_VERIFICATION_REJECTED, denied.decision().denyReason());
    }

    @Test
    void shouldPreserveUnavailableAuthenticationAsDenialRatherThanAnonymousAccess() {
        AccessStageOutput.Denied denied = assertInstanceOf(AccessStageOutput.Denied.class,
                evaluate(service((credentials, clientId, evidence, now) ->
                                CompletableFuture.completedFuture(ClientVerificationOutcome.failed())),
                        command(true, APP, signedEvidence()), absentSubject()));
        assertEquals(AccessDenyReason.CLIENT_VERIFICATION_FAILED, denied.decision().denyReason());
    }

    @Test
    void shouldTranslateSynchronousInvalidEvidenceButPropagateUnexpectedFailures() {
        AccessStageCommand command = command(true, APP, signedEvidence());
        AccessStageOutput.Denied denied = assertInstanceOf(AccessStageOutput.Denied.class,
                evaluate(service((credentials, clientId, evidence, now) -> {
                            throw new IllegalArgumentException("invalid");
                        }), command, absentSubject()));
        assertEquals(AccessDenyReason.CLIENT_VERIFICATION_REJECTED, denied.decision().denyReason());

        CompletionStage<AccessStageOutput> failed = service((credentials, clientId, evidence, now) -> {
            throw new IllegalStateException("unexpected");
        }).evaluate(command, absentSubject());
        CompletionException thrown = assertThrows(CompletionException.class,
                () -> failed.toCompletableFuture().join());
        assertInstanceOf(IllegalStateException.class, thrown.getCause());
    }

    private static AccessStageOutput evaluate(AccessStageCommand command,
            Supplier<CompletionStage<SubjectAuthenticationOutcome>> subjectAuth) {
        return evaluate(service(unusedAuthenticator()), command, subjectAuth);
    }

    private static AccessStageOutput evaluate(AccessStageCommandService service, AccessStageCommand command,
            Supplier<CompletionStage<SubjectAuthenticationOutcome>> subjectAuth) {
        return service.evaluate(command, subjectAuth).toCompletableFuture().join();
    }

    private static AccessStageCommandService service(ClientCredentialAuthenticator authenticator) {
        return new AccessStageCommandService(authenticator, new AccessEvaluator(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AdmissionStageOutput admit(AccessStageOutput.Allowed allowed,
            Map<RateLimitDimension, String> expectedDimensions) {
        CapabilityContext context = new CapabilityContext(CapabilitySlot.POST_AUTH, allowed.prepared().evidence(),
                ((EndpointMatch.Unique) allowed.prepared().endpointMatch()).endpoint(), allowed.decision().grantedContext());
        java.util.List<RateLimitRule> rules =
                java.util.Arrays.stream(RateLimitDimension.values()).map(dimension ->
                        new RateLimitRule(dimension.name(), dimension, 10, 1,
                                RateLimitMode.EXACT)).toList();
        java.util.List<RateLimitConfig> rateLimits = List.of(new RateLimitConfig(rules, null));
        assertEquals(expectedDimensions, new RateLimitCapability().units(new com.cloud.gateway.domain.capability.ratelimit.EffectiveRateLimitPolicy(rateLimits), context).stream()
                .collect(java.util.stream.Collectors.toMap(unit -> unit.quota().rule().dimension(), unit -> unit.quota().key())));
        AdmissionStageCommandService admission = new AdmissionStageCommandService(
                new CapabilityExecutionCommandService(new CapabilityPolicyAssembler(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry()), List.of(new CapabilityRegistration<>(new RateLimitCapability(), config -> config.get(com.cloud.gateway.domain.capability.ratelimit.RateLimitPolicyComposer.INSTANCE))),
                        List.of(new RateLimitCapabilityExecutor(planned -> {
                    throw new AssertionError("no configured quota");
                }))));
        return admission.admit(new AdmissionStageCommand(allowed)).toCompletableFuture().join();
    }

    private static Supplier<CompletionStage<SubjectAuthenticationOutcome>> absentSubject() {
        return subject(SubjectAuthenticationOutcome.absent());
    }

    private static Supplier<CompletionStage<SubjectAuthenticationOutcome>> subject(
            SubjectAuthenticationOutcome outcome) {
        return () -> CompletableFuture.completedFuture(outcome);
    }

    private static ClientCredentialAuthenticator unusedAuthenticator() {
        return (credentials, clientId, evidence, now) -> { throw new AssertionError("authentication must not run"); };
    }

    private static ClientCredentialAuthenticator verifiedAuthenticator() {
        return (credentials, clientId, evidence, now) -> CompletableFuture.completedFuture(verified());
    }

    private static ClientSignatureEvidence signedEvidence() {
        return new ClientSignatureEvidence(new byte[]{1}, "123", "signature");
    }

    private static ClientVerificationOutcome verified() {
        return ClientVerificationOutcome.verified(new VerifiedClient(
                CLIENT, CREDENTIAL, ClientVerificationMode.REQUEST_SIGNATURE, NOW));
    }

    private static AccessStageCommand command(boolean credentials, AppId declaredApp,
            ClientSignatureEvidence signatureEvidence) {
        Credentials pair = credentials
                ? new Credentials(new Credential(CREDENTIAL, "secret-ref",
                        SignatureContract.HTTP_REQUEST_SIGNATURE_V1, NOW.minusSeconds(60), NOW.plusSeconds(3600)), null)
                : null;
        IngressSnapshot snapshot = new IngressSnapshot(new SnapshotVersion(7),
                new IngressContent(
                        new AppRegistry(List.of(new App(APP, "web", AppStatus.ENABLED, new AppAccess(Set.of())))),
                        new ClientRegistry(List.of(new Client(CLIENT, ClientStatus.ENABLED,
                                new ClientAccess(Set.of(APP), pair)))),
                        new EndpointRegistry(List.of(ENDPOINT)), policies()),
                "hash", NOW);
        return new AccessStageCommand(new PreparationStageOutput.Prepared(snapshot,
                EndpointMatch.of(List.of(ENDPOINT)),
                new RequestEvidence("GET", "/test", null, declaredApp, CLIENT, null, "192.0.2.1"),
                CapabilityExecution.empty()),
                signatureEvidence);
    }

    private static AccessStageCommand ambiguousCommand() {
        IngressSnapshot snapshot = new IngressSnapshot(new SnapshotVersion(7),
                new IngressContent(
                        new AppRegistry(List.of(new App(APP, "web", AppStatus.ENABLED, new AppAccess(Set.of())))),
                        new ClientRegistry(List.of()), new EndpointRegistry(List.of(ENDPOINT)), policies()),
                "hash", NOW);
        Endpoint other = new Endpoint(new EndpointId("TEST.OTHER"),
                new com.cloud.gateway.domain.endpoint.ServiceId("service"),
                new com.cloud.gateway.domain.endpoint.RouteId("route"),
                List.of(new EndpointMatcher("GET", "/test/**")), Set.of(), EndpointStatus.ENABLED,
                new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                        SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of()));
        return new AccessStageCommand(new PreparationStageOutput.Prepared(snapshot,
                EndpointMatch.of(List.of(ENDPOINT, other)),
                new RequestEvidence("GET", "/test", null, APP, CLIENT, null, "192.0.2.1"),
                CapabilityExecution.empty()), null);
    }

    private static CapabilityComposition policies() {
        CapabilityBundleId id = new CapabilityBundleId("verified");
        return new CapabilityComposition(
                Map.of(id, new CapabilityBundle(id, java.util.List.of(CLIENT_HEADERS))),
                List.of(new CapabilityBinding(new CapabilityBindingId("verified"), id, null, null, Set.of(), null, CLIENT, null)));
    }
}
