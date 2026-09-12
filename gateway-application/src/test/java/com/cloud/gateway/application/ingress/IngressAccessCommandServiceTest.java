package com.cloud.gateway.application.ingress;

import static org.junit.jupiter.api.Assertions.*;

import com.cloud.gateway.application.security.ClientCredentialAuthenticator;
import com.cloud.gateway.application.security.ClientSignatureEvidence;
import com.cloud.gateway.domain.access.*;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientPolicy;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.access.client.VerifiedClient;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.access.subject.AuthenticatedSubject;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
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
            "service", List.of(new EndpointMatcher("GET", "/test")), "route");
    private static final HeaderPolicy CLIENT_HEADERS = new HeaderPolicy(Set.of(),
            Map.of("X-Gateway-Test", "verified"), Set.of(), CredentialForwarding.stripAll());

    @Test
    void shouldNotPromoteDeclaredClientToContextBindingOrQuotaDimension() {
        IngressAccessCommand command = command(ClientVerificationMode.IGNORE, true, APP);
        IngressAccessOutput.Allowed allowed = assertInstanceOf(IngressAccessOutput.Allowed.class,
                service(unusedAuthenticator()).evaluate(command, ClientVerificationOutcome.absent(),
                        SubjectAuthenticationOutcome.absent()));
        assertNull(allowed.context().clientContext());
        assertFalse(allowed.dimensions().containsKey(RateLimitDimension.CLIENT));
        assertEquals(allowed.headers(), allowed.policy().headers());
        assertEquals(HeaderPolicy.defaults(CredentialForwarding.stripAll()), allowed.headers());
        assertEquals(Map.of(RateLimitDimension.IP, "192.0.2.1", RateLimitDimension.APP, "app",
                RateLimitDimension.ENDPOINT, "TEST.GET"), allowed.dimensions());
        assertThrows(UnsupportedOperationException.class,
                () -> allowed.dimensions().put(RateLimitDimension.CLIENT, "forged"));
    }

    @Test
    void shouldResolveVerifiedClientBindingAndSubjectDimensionAfterAuthorization() {
        IngressAccessCommand command = command(ClientVerificationMode.REQUEST_SIGNATURE, true, APP);
        SubjectAuthenticationOutcome subject = SubjectAuthenticationOutcome.authenticated(new AuthenticatedSubject(
                SubjectType.HOST_SESSION, "user", "session", "issuer", Set.of("api"), Set.of(), NOW.plusSeconds(60)));
        IngressAccessOutput.Allowed allowed = assertInstanceOf(IngressAccessOutput.Allowed.class,
                service(unusedAuthenticator()).evaluate(command, verified(), subject));
        assertEquals(CLIENT, allowed.context().clientContext().clientId());
        assertEquals(CLIENT_HEADERS, allowed.headers());
        assertEquals("client", allowed.dimensions().get(RateLimitDimension.CLIENT));
        assertEquals("HOST_SESSION:user", allowed.dimensions().get(RateLimitDimension.SUBJECT));
    }

    @Test
    void shouldReturnDomainDenialWithCapturedVersionWithoutExecutablePolicy() {
        IngressAccessOutput.Denied denied = assertInstanceOf(IngressAccessOutput.Denied.class,
                service(unusedAuthenticator()).evaluate(command(ClientVerificationMode.IGNORE, true, null),
                        ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.absent()));
        assertEquals(IngressAccessDenyReason.APP_NOT_FOUND, denied.decision().reason());
        assertEquals(7, denied.decision().snapshotVersion());
    }

    @Test
    void shouldUseCapturedCredentialsOriginalEvidenceAndInjectedTime() {
        IngressAccessCommand command = command(ClientVerificationMode.REQUEST_SIGNATURE, true, APP);
        ClientSignatureEvidence evidence = new ClientSignatureEvidence(new byte[]{1, 2}, "123", "signature",
                "GET", "/a%2Fb", "x=1&x=2", "app", "client", "client", "nonce");
        AtomicInteger calls = new AtomicInteger();
        ClientCredentialAuthenticator authenticator = (credentials, original, now) -> {
            calls.incrementAndGet();
            assertSame(command.snapshot().content().clients().get(CLIENT).credentials(), credentials);
            assertSame(evidence, original);
            assertEquals(NOW, now);
            return CompletableFuture.completedFuture(verified());
        };
        assertInstanceOf(ClientVerificationOutcome.Verified.class,
                service(authenticator).authenticateClient(command, evidence).toCompletableFuture().join());
        assertEquals(1, calls.get());
    }

    @Test
    void shouldPreserveMissingCredentialPrecedenceOverMissingEvidence() {
        IngressAccessCommandService service = service(unusedAuthenticator());
        assertInstanceOf(ClientVerificationOutcome.Rejected.class, service.authenticateClient(
                command(ClientVerificationMode.REQUEST_SIGNATURE, false, APP), null).toCompletableFuture().join());
        assertInstanceOf(ClientVerificationOutcome.Absent.class, service.authenticateClient(
                command(ClientVerificationMode.REQUEST_SIGNATURE, true, APP), null).toCompletableFuture().join());
        assertInstanceOf(ClientVerificationOutcome.Absent.class, service.authenticateClient(
                command(ClientVerificationMode.IGNORE, true, APP), null).toCompletableFuture().join());
    }

    @Test
    void shouldPreserveUnavailableAuthenticationAsDenialRatherThanAnonymousAccess() {
        IngressAccessCommand command = command(ClientVerificationMode.REQUEST_SIGNATURE, true, APP);
        IngressAccessOutput.Denied denied = assertInstanceOf(IngressAccessOutput.Denied.class,
                service(unusedAuthenticator()).evaluate(command, ClientVerificationOutcome.failed(),
                        SubjectAuthenticationOutcome.absent()));
        assertEquals(IngressAccessDenyReason.CLIENT_VERIFICATION_FAILED, denied.decision().reason());
    }

    @Test
    void shouldTranslateSynchronousInvalidEvidenceButPropagateUnexpectedFailures() {
        IngressAccessCommand command = command(ClientVerificationMode.REQUEST_SIGNATURE, true, APP);
        ClientSignatureEvidence evidence = new ClientSignatureEvidence(null, "123", "signature");
        assertInstanceOf(ClientVerificationOutcome.Rejected.class,
                service((credentials, original, now) -> { throw new IllegalArgumentException("invalid"); })
                        .authenticateClient(command, evidence).toCompletableFuture().join());
        assertThrows(IllegalStateException.class,
                () -> service((credentials, original, now) -> { throw new IllegalStateException("unexpected"); })
                        .authenticateClient(command, evidence));
    }

    @Test
    void shouldInvokeOnlyRequiredSubjectAuthentication() {
        IngressAccessCommandService service = service(unusedAuthenticator());
        AtomicInteger hosts = new AtomicInteger();
        AtomicInteger browsers = new AtomicInteger();
        for (SubjectAuthenticationRequirement requirement : SubjectAuthenticationRequirement.values()) {
            service.authenticateSubject(requirement, () -> {
                hosts.incrementAndGet();
                return CompletableFuture.completedFuture(SubjectAuthenticationOutcome.absent());
            }, () -> {
                browsers.incrementAndGet();
                return CompletableFuture.completedFuture(SubjectAuthenticationOutcome.rejected());
            }).toCompletableFuture().join();
        }
        assertEquals(2, hosts.get());
        assertEquals(1, browsers.get());
    }

    private static IngressAccessCommandService service(ClientCredentialAuthenticator authenticator) {
        return new IngressAccessCommandService(authenticator, new IngressAccessEvaluator(),
                new PolicyResolver(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ClientCredentialAuthenticator unusedAuthenticator() {
        return (credentials, evidence, now) -> { throw new AssertionError("authentication must not run"); };
    }

    private static ClientVerificationOutcome verified() {
        return ClientVerificationOutcome.verified(new VerifiedClient(
                ClientVerificationMode.REQUEST_SIGNATURE, CREDENTIAL, CLIENT, NOW));
    }

    private static IngressAccessCommand command(ClientVerificationMode requirement, boolean credentials,
                                                AppId declaredApp) {
        ClientPolicy client = new ClientPolicy(CLIENT, Set.of(APP));
        if (credentials) {
            client = client.withCredentials(new Credentials(new Credential(CREDENTIAL, CLIENT,
                    SignatureContract.HTTP_REQUEST_SIGNATURE_V1, "secret-ref", NOW.minusSeconds(60),
                    NOW.plusSeconds(3600)), null));
        }
        EndpointPolicy endpoint = new EndpointPolicy(ENDPOINT.endpointId(), ClientVerificationRequirement.REQUIRED, requirement,
                SubjectAuthenticationRequirement.HOST_SESSION_OPTIONAL, Set.of(), Set.of("api"), CredentialForwarding.stripAll(),
                EndpointStatus.ENABLED);
        PolicyBundleId id = new PolicyBundleId("verified");
        PolicyComposition policies = new PolicyComposition(
                Map.of(id, new PolicyBundle(id, null, null, null, null, CLIENT_HEADERS)),
                List.of(new PolicyBinding(new PolicyBindingId("verified"), id, null, null, Set.of(), null, CLIENT, null)));
        IngressAccessSnapshot snapshot = new IngressAccessSnapshot(7,
                new IngressAccessContent(new EndpointRegistry(List.of(ENDPOINT)),
                        Map.of(APP, new AppPolicy(APP, "web", Set.of(), PolicyStatus.ENABLED)),
                        Map.of(CLIENT, client), Map.of(ENDPOINT.endpointId(), endpoint), policies), "hash", NOW);
        return new IngressAccessCommand(snapshot, ENDPOINT, declaredApp, CLIENT, null, false, "192.0.2.1");
    }
}
