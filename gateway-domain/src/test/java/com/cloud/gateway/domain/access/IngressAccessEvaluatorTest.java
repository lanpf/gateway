package com.cloud.gateway.domain.access;

import com.cloud.framework.core.SubjectType;
import com.cloud.gateway.domain.access.app.VerifiedApp;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.access.client.VerifiedClient;
import com.cloud.gateway.domain.access.subject.AuthenticatedSubject;
import com.cloud.gateway.domain.access.subject.SessionId;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationFailReason;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationRejectReason;
import com.cloud.gateway.domain.access.subject.SubjectId;
import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppAccess;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.policy.bundle.PolicyConfig;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class IngressAccessEvaluatorTest {
    private static final EndpointId ENDPOINT_ID = new EndpointId("AUTHENTICATED");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-06-01T00:00:00Z");

    private final IngressAccessEvaluator evaluator = new IngressAccessEvaluator();

    @Test
    void shouldAllowWhenClientSubjectAudienceAndScopeAllMatch() {
        IngressAccessDecision decision = evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, route())),
                request(ENDPOINT_ID, declaredClient(), "https://h5.example.com", false),
                ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.authenticated(subject()));

        IngressAccessDecision.Allowed allowed = assertInstanceOf(IngressAccessDecision.Allowed.class, decision);
        VerifiedApp app = allowed.grantedContext().app();
        assertEquals("mini-program", app.appId().value());
        assertEquals("WECHAT_MINI_PROGRAM", app.platform());
        assertNull(allowed.grantedContext().client());
        assertEquals("user-1", allowed.grantedContext().subject().subjectId().value());
        assertEquals(7, allowed.snapshotVersion().value());
    }

    @Test
    void shouldBuildClientContextOnlyFromVerifiedCredential() {
        var clientId = new ClientId("DIRECT");
        var credentialId = new CredentialId("verified-client");
        var verified = new VerifiedClient(clientId, credentialId,
                ClientVerificationMode.REQUEST_SIGNATURE, Instant.parse("2026-01-01T00:00:00Z"));
        IngressAccessDecision.Allowed allowed = assertInstanceOf(IngressAccessDecision.Allowed.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, optionalSubjectRoute())),
                        request(ENDPOINT_ID, clientId, "https://h5.example.com", false),
                        ClientVerificationOutcome.verified(verified), SubjectAuthenticationOutcome.absent()));
        assertEquals(clientId, allowed.grantedContext().client().clientId());
        assertEquals(credentialId, allowed.grantedContext().client().credentialId());
        assertNull(allowed.grantedContext().subject());
    }

    @Test
    void shouldDenyWhenRequiredAudienceIsMissing() {
        AuthenticatedSubject subject = new AuthenticatedSubject(SubjectType.HOST_SESSION, new SubjectId("user-1"),
                new SessionId("session-1"), "issuer", Set.of("another-api"), Set.of("read"),
                Instant.parse("2030-01-01T00:00:00Z"));

        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, route())),
                        request(ENDPOINT_ID, declaredClient(), "https://h5.example.com", false),
                        ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.authenticated(subject)));

        assertEquals(IngressAccessDenyReason.AUDIENCE_NOT_ALLOWED, denied.denyReason());
    }

    @Test
    void shouldDenyWhenRequiredScopeIsMissing() {
        AuthenticatedSubject subject = new AuthenticatedSubject(SubjectType.HOST_SESSION, new SubjectId("user-1"),
                new SessionId("session-1"), "issuer", Set.of("cloud-api"), Set.of("other"),
                Instant.parse("2030-01-01T00:00:00Z"));

        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, route())),
                        request(ENDPOINT_ID, declaredClient(), "https://h5.example.com", false),
                        ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.authenticated(subject)));

        assertEquals(IngressAccessDenyReason.SCOPE_NOT_ALLOWED, denied.denyReason());
    }

    @Test
    void shouldDenyVerifiedClientWithoutDeclaration() {
        VerifiedClient verified = new VerifiedClient(new ClientId("DIRECT"), new CredentialId("verified-client"),
                ClientVerificationMode.REQUEST_SIGNATURE, Instant.now());

        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, route())),
                        request(ENDPOINT_ID, null, "https://h5.example.com", false),
                        ClientVerificationOutcome.verified(verified), SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.CLIENT_VERIFICATION_MISMATCH, denied.denyReason());
    }

    @Test
    void shouldDenyVerifiedClientMismatchingDeclaration() {
        VerifiedClient verified = new VerifiedClient(new ClientId("OTHER"), new CredentialId("verified-client"),
                ClientVerificationMode.REQUEST_SIGNATURE, Instant.now());

        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, route())),
                        request(ENDPOINT_ID, declaredClient(), "https://h5.example.com", false),
                        ClientVerificationOutcome.verified(verified), SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.CLIENT_VERIFICATION_MISMATCH, denied.denyReason());
    }

    @Test
    void shouldAllowAnonymousRequestOnOptionalEndpoint() {
        IngressAccessDecision decision = evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, optionalSubjectRoute())),
                request(ENDPOINT_ID, null, "https://h5.example.com", false),
                ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.absent());

        assertInstanceOf(IngressAccessDecision.Allowed.class, decision);
    }

    @Test
    void shouldRequireClientDeclarationOnRequiredEndpoint() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, signedRoute())),
                        request(ENDPOINT_ID, null, "https://h5.example.com", false),
                        ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.CLIENT_REQUIRED, denied.denyReason());
    }

    @Test
    void shouldRequireVerificationOnRequiredEndpoint() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, signedRoute())),
                        request(ENDPOINT_ID, declaredClient(), "https://h5.example.com", false),
                        ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.CLIENT_VERIFICATION_REQUIRED, denied.denyReason());
    }

    @Test
    void shouldDenyRejectedClientVerification() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, signedRoute())),
                        request(ENDPOINT_ID, declaredClient(), "https://h5.example.com", false),
                        ClientVerificationOutcome.rejected(), SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.CLIENT_VERIFICATION_REJECTED, denied.denyReason());
    }

    @Test
    void shouldFailClosedWhenClientCredentialDependencyIsUnavailable() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, signedRoute())),
                        request(ENDPOINT_ID, declaredClient(), "https://h5.example.com", false),
                        ClientVerificationOutcome.failed(), SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.CLIENT_VERIFICATION_FAILED, denied.denyReason());
    }

    @Test
    void shouldDenyUnregisteredClient() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, route())),
                        request(ENDPOINT_ID, new ClientId("UNKNOWN"), "https://h5.example.com", false),
                        ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.CLIENT_NOT_FOUND, denied.denyReason());
    }

    @Test
    void shouldDenyClientNotAllowedForApp() {
        Client otherAppClient = new Client(new ClientId("OTHER"), ClientStatus.ENABLED,
                new ClientAccess(Set.of(new AppId("other-app")), credentials("other-credential")));
        App otherApp = new App(new AppId("other-app"), "WEB", AppStatus.ENABLED, new AppAccess(Set.of()));

        IngressAccessSnapshot snapshot = new IngressAccessSnapshot(new AccessSnapshotVersion(7),
                new IngressAccessContent(new AppRegistry(List.of(app(), otherApp)),
                        new ClientRegistry(List.of(client(), otherAppClient)),
                        new EndpointRegistry(List.of(endpoint(ENDPOINT_ID, route()))), PolicyConfig.empty()),
                "content-hash", PUBLISHED_AT);

        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot,
                        request(ENDPOINT_ID, new ClientId("OTHER"), "https://h5.example.com", false),
                        ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.CLIENT_NOT_ALLOWED, denied.denyReason());
    }

    @Test
    void shouldAllowOptionalSubjectOnlyWhenOutcomeIsAbsent() {
        IngressAccessDecision decision = evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, optionalSubjectRoute())),
                request(ENDPOINT_ID, declaredClient(), "https://h5.example.com", false),
                ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.absent());

        IngressAccessDecision.Allowed allowed = assertInstanceOf(IngressAccessDecision.Allowed.class, decision);
        assertNull(allowed.grantedContext().subject());
    }

    @Test
    void shouldTreatExpiredSubjectAsAbsentForOptionalEndpoint() {
        IngressAccessDecision decision = evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, optionalSubjectRoute())),
                request(ENDPOINT_ID, declaredClient(), "https://h5.example.com", false),
                ClientVerificationOutcome.absent(),
                SubjectAuthenticationOutcome.rejected(SubjectAuthenticationRejectReason.EXPIRED));

        assertInstanceOf(IngressAccessDecision.Allowed.class, decision);
    }

    @Test
    void shouldRejectInvalidCredentialForOptionalSubject() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, optionalSubjectRoute())),
                        request(ENDPOINT_ID, declaredClient(), "https://h5.example.com", false),
                        ClientVerificationOutcome.absent(),
                        SubjectAuthenticationOutcome.rejected(SubjectAuthenticationRejectReason.INVALID)));

        assertEquals(IngressAccessDenyReason.SUBJECT_AUTHENTICATION_REJECTED, denied.denyReason());
    }

    @Test
    void shouldRejectExpiredSubjectForRequiredEndpoint() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, route())),
                        request(ENDPOINT_ID, declaredClient(), "https://h5.example.com", false),
                        ClientVerificationOutcome.absent(),
                        SubjectAuthenticationOutcome.rejected(SubjectAuthenticationRejectReason.EXPIRED)));

        assertEquals(IngressAccessDenyReason.SUBJECT_AUTHENTICATION_REJECTED, denied.denyReason());
    }

    @Test
    void shouldRequireSubjectOnRequiredEndpoint() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, route())),
                        request(ENDPOINT_ID, declaredClient(), "https://h5.example.com", false),
                        ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.SUBJECT_REQUIRED, denied.denyReason());
    }

    @Test
    void shouldPreserveSubjectAuthenticationDependencyFailure() {
        IngressAccessDecision.Denied unavailable = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, route())),
                        request(ENDPOINT_ID, declaredClient(), "https://h5.example.com", false),
                        ClientVerificationOutcome.absent(),
                        SubjectAuthenticationOutcome.failed(SubjectAuthenticationFailReason.UPSTREAM_UNAVAILABLE)));
        IngressAccessDecision.Denied invalid = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, route())),
                        request(ENDPOINT_ID, declaredClient(), "https://h5.example.com", false),
                        ClientVerificationOutcome.absent(),
                        SubjectAuthenticationOutcome.failed(SubjectAuthenticationFailReason.RESPONSE_INVALID)));

        assertEquals(IngressAccessDenyReason.SUBJECT_AUTHENTICATION_UPSTREAM_UNAVAILABLE, unavailable.denyReason());
        assertEquals(IngressAccessDenyReason.SUBJECT_AUTHENTICATION_RESPONSE_INVALID, invalid.denyReason());
    }

    @Test
    void shouldRequireAllowedOriginForUnsafeBrowserSessionRequest() {
        IngressAccessDecision.Denied missing = assertInstanceOf(
                IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, browserRoute())),
                        request(ENDPOINT_ID, null, null, true),
                        ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.authenticated(browserSubject())));
        IngressAccessDecision.Denied mismatched = assertInstanceOf(
                IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, browserRoute())),
                        request(ENDPOINT_ID, null, "https://evil.example.com", true),
                        ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.authenticated(browserSubject())));

        assertEquals(IngressAccessDenyReason.BROWSER_ORIGIN_NOT_ALLOWED, missing.denyReason());
        assertEquals(IngressAccessDenyReason.BROWSER_ORIGIN_NOT_ALLOWED, mismatched.denyReason());
    }

    @Test
    void shouldAllowMatchingOriginForUnsafeBrowserSessionRequest() {
        IngressAccessDecision decision = evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, browserRoute())),
                request(ENDPOINT_ID, null, "https://h5.example.com", true),
                ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.authenticated(browserSubject()));

        assertInstanceOf(IngressAccessDecision.Allowed.class, decision);
    }

    @Test
    void shouldNotRequireOriginForSafeBrowserSessionRequest() {
        IngressAccessDecision decision = evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, browserRoute())),
                request(ENDPOINT_ID, null, null, false),
                ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.authenticated(browserSubject()));

        assertInstanceOf(IngressAccessDecision.Allowed.class, decision);
    }

    @Test
    void shouldDenyUnregisteredEndpoint() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, route())),
                        request(new EndpointId("UNKNOWN"), null, "https://h5.example.com", false),
                        ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.ENDPOINT_NOT_FOUND, denied.denyReason());
    }

    @Test
    void shouldDenyDisabledEndpointAsNotFound() {
        Endpoint disabled = new Endpoint(ENDPOINT_ID, new ServiceId("SVC"), new RouteId("ROUTE"),
                List.of(new EndpointMatcher("GET", "/x")), Set.of(), EndpointStatus.DISABLED, route());

        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(disabled),
                        request(ENDPOINT_ID, null, "https://h5.example.com", false),
                        ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.ENDPOINT_NOT_FOUND, denied.denyReason());
    }

    @Test
    void shouldDenyUnregisteredApp() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot(endpoint(ENDPOINT_ID, route())),
                        new IngressAccessRequest(ENDPOINT_ID, new AppId("UNKNOWN"), null, "https://h5.example.com", false),
                        ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.APP_NOT_FOUND, denied.denyReason());
    }

    private static IngressAccessSnapshot snapshot(Endpoint endpoint) {
        return new IngressAccessSnapshot(new AccessSnapshotVersion(7),
                new IngressAccessContent(new AppRegistry(List.of(app())), new ClientRegistry(List.of(client())),
                        new EndpointRegistry(List.of(endpoint)), PolicyConfig.empty()),
                "content-hash", PUBLISHED_AT);
    }

    private static IngressAccessRequest request(EndpointId endpointId, ClientId clientId, String origin, boolean unsafe) {
        return new IngressAccessRequest(endpointId, new AppId("mini-program"), clientId, origin, unsafe);
    }

    private static ClientId declaredClient() {
        return new ClientId("DIRECT");
    }

    private static App app() {
        return new App(new AppId("mini-program"), "WECHAT_MINI_PROGRAM", AppStatus.ENABLED,
                new AppAccess(Set.of("https://h5.example.com")));
    }

    private static Client client() {
        return new Client(declaredClient(), ClientStatus.ENABLED,
                new ClientAccess(Set.of(new AppId("mini-program")), credentials()));
    }

    private static Credentials credentials() {
        return credentials("verified-client");
    }

    private static Credentials credentials(String credentialId) {
        return new Credentials(new Credential(new CredentialId(credentialId), "secret-ref",
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1, Instant.EPOCH,
                Instant.parse("2099-01-01T00:00:00Z")), null);
    }

    private static Endpoint endpoint(EndpointId endpointId, EndpointAccess policy) {
        return new Endpoint(endpointId, new ServiceId("SVC"), new RouteId("ROUTE"),
                List.of(new EndpointMatcher("GET", "/x")), Set.of(), EndpointStatus.ENABLED, policy);
    }

    /** Client declaration optional; host subject required with attached access requirements. */
    private static EndpointAccess route() {
        return new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                SubjectAuthenticationRequirement.REQUIRED, Set.of("read"), Set.of("cloud-api"));
    }

    private static EndpointAccess optionalSubjectRoute() {
        return new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                SubjectAuthenticationRequirement.OPTIONAL, Set.of("read"), Set.of("cloud-api"));
    }

    /** Signature-verified client required; no subject involvement. */
    private static EndpointAccess signedRoute() {
        return new EndpointAccess(ClientVerificationRequirement.REQUIRED,
                SubjectAuthenticationRequirement.OPTIONAL, Set.of(), Set.of());
    }

    private static EndpointAccess browserRoute() {
        return new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                SubjectAuthenticationRequirement.OPTIONAL, Set.of(), Set.of());
    }

    private static AuthenticatedSubject subject() {
        return new AuthenticatedSubject(SubjectType.HOST_SESSION, new SubjectId("user-1"), new SessionId("session-1"),
                "issuer", Set.of("cloud-api"), Set.of("read"), Instant.parse("2030-01-01T00:00:00Z"));
    }

    private static AuthenticatedSubject browserSubject() {
        return new AuthenticatedSubject(SubjectType.BROWSER_SESSION, new SubjectId("user-1"),
                new SessionId("session-1"), null, null, null, Instant.parse("2030-01-01T00:00:00Z"));
    }
}
