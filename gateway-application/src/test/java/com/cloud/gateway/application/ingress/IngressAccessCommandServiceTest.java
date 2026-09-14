package com.cloud.gateway.application.ingress;

import static org.junit.jupiter.api.Assertions.*;

import com.cloud.gateway.application.security.ClientCredentialAuthenticator;
import com.cloud.gateway.application.security.ClientSignatureEvidence;
import com.cloud.framework.core.SubjectType;
import com.cloud.gateway.domain.access.*;
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
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.access.client.VerifiedClient;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointMatch;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.policy.bundle.*;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.access.subject.AuthenticatedSubject;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationRejectReason;
import com.cloud.gateway.domain.policy.bundle.*;
import com.cloud.gateway.domain.policy.capability.RateLimitDimension;
import com.cloud.gateway.domain.policy.header.HeaderPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IngressAccessCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");
    private static final AppId APP = new AppId("app");
    private static final ClientId CLIENT = new ClientId("client");
    private static final CredentialId CREDENTIAL = new CredentialId("client");
    private static final Endpoint ENDPOINT = new Endpoint(new EndpointId("TEST.GET"),
            new com.cloud.gateway.domain.endpoint.ServiceId("service"),
            new com.cloud.gateway.domain.endpoint.RouteId("route"),
            List.of(new EndpointMatcher("GET", "/test")), Set.of(), EndpointStatus.ENABLED,
            new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                    SubjectAuthenticationRequirement.OPTIONAL, Set.of(), Set.of()));
    private static final HeaderPolicy CLIENT_HEADERS = new HeaderPolicy(Set.of(),
            Map.of("X-Gateway-Test", "verified"), Set.of(), com.cloud.gateway.domain.policy.CredentialForwarding.stripAll());

    @Test
    void shouldNotPromoteDeclaredClientToContextBindingOrQuotaDimension() {
        IngressAccessCommand command = command(true, APP);
        IngressAccessOutput.Allowed allowed = assertInstanceOf(IngressAccessOutput.Allowed.class,
                service(unusedAuthenticator()).evaluate(command, ClientVerificationOutcome.absent(),
                        SubjectAuthenticationOutcome.absent()));
        assertNull(allowed.context().client());
        assertFalse(allowed.dimensions().containsKey(RateLimitDimension.CLIENT));
        assertEquals(HeaderPolicy.defaults(com.cloud.gateway.domain.policy.CredentialForwarding.stripAll()),
                allowed.headers());
        assertEquals(Map.of(RateLimitDimension.IP, "192.0.2.1", RateLimitDimension.APP, "app",
                RateLimitDimension.ENDPOINT, "TEST.GET"), allowed.dimensions());
        assertThrows(UnsupportedOperationException.class,
                () -> allowed.dimensions().put(RateLimitDimension.CLIENT, "forged"));
    }

    @Test
    void shouldResolveVerifiedClientBindingAndSubjectDimensionAfterAuthorization() {
        IngressAccessCommand command = command(true, APP);
        SubjectAuthenticationOutcome subject = SubjectAuthenticationOutcome.authenticated(new AuthenticatedSubject(
                SubjectType.HOST_SESSION, new com.cloud.gateway.domain.access.subject.SubjectId("user"),
                new com.cloud.gateway.domain.access.subject.SessionId("session"), "issuer", Set.of("api"), Set.of(),
                NOW.plusSeconds(60)));
        IngressAccessOutput.Allowed allowed = assertInstanceOf(IngressAccessOutput.Allowed.class,
                service(unusedAuthenticator()).evaluate(command, verified(), subject));
        assertEquals(CLIENT, allowed.context().client().clientId());
        assertEquals(CLIENT_HEADERS, allowed.headers());
        assertEquals("client", allowed.dimensions().get(RateLimitDimension.CLIENT));
        assertEquals("HOST_SESSION:user", allowed.dimensions().get(RateLimitDimension.SUBJECT));
    }

    @Test
    void shouldReturnDomainDenialWithCapturedVersionWithoutExecutablePolicy() {
        IngressAccessOutput.Denied denied = assertInstanceOf(IngressAccessOutput.Denied.class,
                service(unusedAuthenticator()).evaluate(command(true, null),
                        ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.absent()));
        assertEquals(IngressAccessDenyReason.APP_NOT_FOUND, denied.decision().denyReason());
        assertEquals(7, denied.decision().snapshotVersion().value());
    }

    @Test
    void shouldDenyAmbiguousCandidatesWithoutJoiningAuthentication() {
        IngressAccessOutput.Denied denied = assertInstanceOf(IngressAccessOutput.Denied.class,
                service(unusedAuthenticator()).evaluate(ambiguousCommand(),
                        ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.absent()));
        assertEquals(IngressAccessDenyReason.ENDPOINT_NOT_FOUND, denied.decision().denyReason());
    }

    @Test
    void shouldUseCapturedCredentialsOriginalEvidenceAndInjectedTime() {
        IngressAccessCommand command = command(true, APP);
        ClientSignatureEvidence evidence = new ClientSignatureEvidence(new byte[]{1, 2}, "123", "signature",
                "GET", "/a%2Fb", "x=1&x=2", "app", "client", "client", "nonce");
        AtomicInteger calls = new AtomicInteger();
        ClientCredentialAuthenticator authenticator = (credentials, clientId, original, now) -> {
            calls.incrementAndGet();
            assertSame(command.snapshot().content().clientRegistry().find(CLIENT).orElseThrow()
                    .access().credentials(), credentials);
            assertSame(evidence, original);
            assertEquals(CLIENT, clientId);
            assertEquals(NOW, now);
            return CompletableFuture.completedFuture(verified());
        };
        assertInstanceOf(ClientVerificationOutcome.Verified.class,
                service(authenticator).authenticateClient(command, evidence).toCompletableFuture().join());
        assertEquals(1, calls.get());
    }

    @Test
    void shouldNotAttemptVerificationWithoutCredentialsOrEvidence() {
        IngressAccessCommandService service = service(unusedAuthenticator());
        assertInstanceOf(ClientVerificationOutcome.Absent.class, service.authenticateClient(
                command(false, APP), null).toCompletableFuture().join());
        assertInstanceOf(ClientVerificationOutcome.Absent.class, service.authenticateClient(
                command(true, APP), null).toCompletableFuture().join());
        assertInstanceOf(ClientVerificationOutcome.Rejected.class, service.authenticateClient(
                command(false, APP), signedEvidence()).toCompletableFuture().join());
    }

    @Test
    void shouldPreserveUnavailableAuthenticationAsDenialRatherThanAnonymousAccess() {
        IngressAccessCommand command = command(true, APP);
        IngressAccessOutput.Denied denied = assertInstanceOf(IngressAccessOutput.Denied.class,
                service(unusedAuthenticator()).evaluate(command, ClientVerificationOutcome.failed(),
                        SubjectAuthenticationOutcome.absent()));
        assertEquals(IngressAccessDenyReason.CLIENT_VERIFICATION_FAILED, denied.decision().denyReason());
    }

    @Test
    void shouldTranslateSynchronousInvalidEvidenceButPropagateUnexpectedFailures() {
        IngressAccessCommand command = command(true, APP);
        ClientSignatureEvidence evidence = new ClientSignatureEvidence(null, "123", "signature");
        assertInstanceOf(ClientVerificationOutcome.Rejected.class,
                service((credentials, clientId, original, now) -> { throw new IllegalArgumentException("invalid"); })
                        .authenticateClient(command, evidence).toCompletableFuture().join());
        assertThrows(IllegalStateException.class,
                () -> service((credentials, clientId, original, now) -> { throw new IllegalStateException("unexpected"); })
                        .authenticateClient(command, evidence));
    }

    private static IngressAccessCommandService service(ClientCredentialAuthenticator authenticator) {
        return new IngressAccessCommandService(authenticator, new IngressAccessEvaluator(),
                new PolicyResolver(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ClientCredentialAuthenticator unusedAuthenticator() {
        return (credentials, clientId, evidence, now) -> { throw new AssertionError("authentication must not run"); };
    }

    private static ClientSignatureEvidence signedEvidence() {
        return new ClientSignatureEvidence(new byte[]{1}, "123", "signature");
    }

    private static ClientVerificationOutcome verified() {
        return ClientVerificationOutcome.verified(new VerifiedClient(
                CLIENT, CREDENTIAL, ClientVerificationMode.REQUEST_SIGNATURE, NOW));
    }

    private static IngressAccessCommand command(boolean credentials, AppId declaredApp) {
        Credentials pair = credentials
                ? new Credentials(new Credential(CREDENTIAL, "secret-ref",
                        SignatureContract.HTTP_REQUEST_SIGNATURE_V1, NOW.minusSeconds(60), NOW.plusSeconds(3600)), null)
                : null;
        IngressAccessSnapshot snapshot = new IngressAccessSnapshot(new AccessSnapshotVersion(7),
                new IngressAccessContent(
                        new AppRegistry(List.of(new App(APP, "web", AppStatus.ENABLED, new AppAccess(Set.of())))),
                        new ClientRegistry(List.of(new Client(CLIENT, ClientStatus.ENABLED,
                                new ClientAccess(Set.of(APP), pair)))),
                        new EndpointRegistry(List.of(ENDPOINT)), policies()),
                "hash", NOW);
        return new IngressAccessCommand(snapshot, EndpointMatch.of(List.of(ENDPOINT)),
                new RequestEvidence("GET", "/test", null, declaredApp, CLIENT, null, "192.0.2.1"));
    }

    private static IngressAccessCommand ambiguousCommand() {
        IngressAccessSnapshot snapshot = new IngressAccessSnapshot(new AccessSnapshotVersion(7),
                new IngressAccessContent(
                        new AppRegistry(List.of(new App(APP, "web", AppStatus.ENABLED, new AppAccess(Set.of())))),
                        new ClientRegistry(List.of()), new EndpointRegistry(List.of(ENDPOINT)), policies()),
                "hash", NOW);
        Endpoint other = new Endpoint(new EndpointId("TEST.OTHER"),
                new com.cloud.gateway.domain.endpoint.ServiceId("service"),
                new com.cloud.gateway.domain.endpoint.RouteId("route"),
                List.of(new EndpointMatcher("GET", "/test/**")), Set.of(), EndpointStatus.ENABLED,
                new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                        SubjectAuthenticationRequirement.OPTIONAL, Set.of(), Set.of()));
        return new IngressAccessCommand(snapshot, EndpointMatch.of(List.of(ENDPOINT, other)),
                new RequestEvidence("GET", "/test", null, APP, CLIENT, null, "192.0.2.1"));
    }

    private static PolicyConfig policies() {
        PolicyBundleId id = new PolicyBundleId("verified");
        return new PolicyConfig(
                Map.of(id, new PolicyBundle(id, null, null, null, null, CLIENT_HEADERS)),
                List.of(new PolicyBinding(new PolicyBindingId("verified"), id, null, null, Set.of(), null, CLIENT, null)));
    }
}
