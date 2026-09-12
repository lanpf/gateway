package com.cloud.gateway.domain.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.access.client.VerifiedClient;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.access.subject.AuthenticatedSubject;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationFailReason;
import com.cloud.gateway.domain.endpoint.SubjectAuthencationRequirement;
import com.cloud.gateway.domain.policy.AuthorizationHeaderForwardingMode;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.client.ClientPolicy;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;

import java.time.Instant;
import java.util.Set;

import com.cloud.gateway.domain.client.CredentialId;
import org.junit.jupiter.api.Test;

class IngressAccessEvaluatorTest {
    private final IngressAccessEvaluator evaluator = new IngressAccessEvaluator();

    @Test
    void shouldAllowWhenClientSubjectAudienceAndScopeAllMatch() {
        IngressAccessDecision decision = evaluator.evaluate(
                7, new ClientId("DIRECT"), app(), client(), route(),
                null, false,
                ClientVerificationOutcome.absent(),
                authenticated(subject()));

        IngressAccessDecision.Allowed allowed = assertInstanceOf(IngressAccessDecision.Allowed.class, decision);
        assertEquals("mini-program", allowed.context().appContext().appId().value());
        assertEquals("user-1", allowed.context().subjectContext().subjectId());
        assertNull(allowed.context().clientContext());
    }

    @Test
    void shouldBuildClientContextOnlyFromAuthenticatedCredential() {
        var clientId = new ClientId("DIRECT");
        var credentialId = new CredentialId("verified-client");
        var verified = new VerifiedClient(ClientVerificationMode.REQUEST_SIGNATURE,
                credentialId, clientId, Instant.parse("2026-01-01T00:00:00Z"));
        IngressAccessDecision.Allowed allowed = assertInstanceOf(IngressAccessDecision.Allowed.class,
                evaluator.evaluate(7, clientId, app(), client(), signedRoute(), null, false,
                        ClientVerificationOutcome.verified(verified), SubjectAuthenticationOutcome.absent()));
        assertEquals(clientId, allowed.context().clientContext().clientId());
        assertEquals(credentialId, allowed.context().clientContext().credentialId());
        assertNull(allowed.context().subjectContext());
    }

    @Test
    void shouldDenyWhenRequiredAudienceIsMissing() {
        AuthenticatedSubject subject = new AuthenticatedSubject(SubjectType.HOST_SESSION, "user-1", "session-1",
                "issuer", Set.of("another-api"), Set.of("read"), Instant.parse("2030-01-01T00:00:00Z"));

        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(7, new ClientId("DIRECT"), app(), client(), route(),
                        null, false,
                        ClientVerificationOutcome.absent(),
                        authenticated(subject)));

        assertEquals(IngressAccessDenyReason.AUDIENCE_NOT_ALLOWED, denied.reason());
    }

    @Test
    void shouldRejectClientForForbiddenRoute() {
        EndpointPolicy route = new EndpointPolicy(new EndpointId("PUBLIC"), ClientVerificationRequirement.FORBIDDEN, ClientVerificationMode.IGNORE, SubjectAuthencationRequirement.IGNORE, Set.of(), Set.of(), forwarding(), EndpointStatus.ENABLED);

        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(1, new ClientId("DIRECT"), app(), client(), route,
                        null, false,
                        ClientVerificationOutcome.absent(),
                        SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.CLIENT_NOT_PERMITTED_FOR_ENDPOINT, denied.reason());
    }

    @Test
    void shouldAllowOptionalHostSessionOnlyWhenCredentialIsAbsent() {
        EndpointPolicy route = optionalHostRoute();

        IngressAccessDecision decision = evaluator.evaluate(
                7, new ClientId("DIRECT"), app(), client(), route,
                        null, false,
                        ClientVerificationOutcome.absent(),
                        SubjectAuthenticationOutcome.absent());

        IngressAccessDecision.Allowed allowed = assertInstanceOf(IngressAccessDecision.Allowed.class, decision);
        assertNull(allowed.context().subjectContext());
    }

    @Test
    void shouldRejectInvalidCredentialForOptionalHostSession() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(7, new ClientId("DIRECT"), app(), client(), optionalHostRoute(),
                        null, false,
                        ClientVerificationOutcome.absent(),
                        SubjectAuthenticationOutcome.rejected()));

        assertEquals(IngressAccessDenyReason.SUBJECT_AUTHENTICATION_REJECTED, denied.reason());
    }

    @Test
    void shouldPreserveSubjectAuthenticationDependencyFailure() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(7, new ClientId("DIRECT"), app(), client(), route(),
                        null, false,
                        ClientVerificationOutcome.absent(),
                        SubjectAuthenticationOutcome.failed(
                                SubjectAuthenticationFailReason.UPSTREAM_UNAVAILABLE)));

        assertEquals(IngressAccessDenyReason.SUBJECT_AUTHENTICATION_UPSTREAM_UNAVAILABLE,
                denied.reason());
    }

    @Test
    void shouldRequireMissingClientCredential() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(7, new ClientId("DIRECT"), app(), client(), signedRoute(),
                        null, false,
                        ClientVerificationOutcome.absent(),
                        SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.CLIENT_VERIFICATION_REQUIRED, denied.reason());
    }

    @Test
    void shouldFailClosedWhenClientCredentialDependencyIsUnavailable() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(7, new ClientId("DIRECT"), app(), client(), signedRoute(),
                        null, false,
                        ClientVerificationOutcome.failed(),
                        SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.CLIENT_VERIFICATION_FAILED, denied.reason());
    }

    @Test
    void shouldRejectInvalidClientCredential() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(7, new ClientId("DIRECT"), app(), client(), signedRoute(),
                        null, false,
                        ClientVerificationOutcome.rejected(),
                        SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.CLIENT_VERIFICATION_MISMATCH, denied.reason());
    }

    @Test
    void shouldRequireAllowedOriginForUnsafeBrowserSessionRequest() {
        IngressAccessDecision.Denied missing = assertInstanceOf(
                IngressAccessDecision.Denied.class,
                evaluator.evaluate(
                        7, new ClientId("DIRECT"), app(), client(), browserRoute(),
                        null, true,
                        ClientVerificationOutcome.absent(),
                        authenticated(browserSubject())));
        IngressAccessDecision.Denied mismatched = assertInstanceOf(
                IngressAccessDecision.Denied.class,
                evaluator.evaluate(
                        7, new ClientId("DIRECT"), app(), client(), browserRoute(),
                        "https://evil.example.com", true,
                        ClientVerificationOutcome.absent(),
                        authenticated(browserSubject())));

        assertEquals(IngressAccessDenyReason.BROWSER_ORIGIN_NOT_ALLOWED, missing.reason());
        assertEquals(IngressAccessDenyReason.BROWSER_ORIGIN_NOT_ALLOWED, mismatched.reason());
    }

    @Test
    void shouldAllowMatchingOriginForUnsafeBrowserSessionRequest() {
        IngressAccessDecision decision = evaluator.evaluate(
                7, new ClientId("DIRECT"), app(), client(), browserRoute(),
                "https://h5.example.com", true,
                ClientVerificationOutcome.absent(),
                authenticated(browserSubject()));

        assertInstanceOf(IngressAccessDecision.Allowed.class, decision);
    }

    @Test
    void shouldNotRequireOriginForSafeBrowserSessionRequest() {
        IngressAccessDecision decision = evaluator.evaluate(
                7, new ClientId("DIRECT"), app(), client(), browserRoute(),
                null, false,
                ClientVerificationOutcome.absent(),
                authenticated(browserSubject()));

        assertInstanceOf(IngressAccessDecision.Allowed.class, decision);
    }

    private static AppPolicy app() {
        return new AppPolicy(
                new AppId("mini-program"),
                "WECHAT_MINI_PROGRAM",
                Set.of("https://h5.example.com"),
                PolicyStatus.ENABLED);
    }
    private static ClientPolicy client() {
        return new ClientPolicy(new ClientId("DIRECT"), Set.of(new AppId("mini-program")));
    }
    private static EndpointPolicy route() {
        return new EndpointPolicy(new EndpointId("AUTHENTICATED"), ClientVerificationRequirement.REQUIRED, ClientVerificationMode.IGNORE, SubjectAuthencationRequirement.HOST_SESSION_REQUIRED, Set.of("read"), Set.of("cloud-api"), forwarding(), EndpointStatus.ENABLED);
    }
    private static AuthenticatedSubject subject() {
        return new AuthenticatedSubject(SubjectType.HOST_SESSION, "user-1", "session-1", "issuer",
                Set.of("cloud-api"), Set.of("read"), Instant.parse("2030-01-01T00:00:00Z"));
    }
    private static SubjectAuthenticationOutcome authenticated(AuthenticatedSubject subject) {
        return SubjectAuthenticationOutcome.authenticated(subject);
    }
    private static EndpointPolicy browserRoute() {
        return new EndpointPolicy(new EndpointId("BROWSER"), ClientVerificationRequirement.REQUIRED, ClientVerificationMode.IGNORE, SubjectAuthencationRequirement.BROWSER_SESSION_REQUIRED, Set.of(), Set.of(), forwarding(), EndpointStatus.ENABLED);
    }
    private static AuthenticatedSubject browserSubject() {
        return new AuthenticatedSubject(
                SubjectType.BROWSER_SESSION,
                "user-1",
                "session-1",
                null,
                Set.of(),
                Set.of(),
                Instant.parse("2030-01-01T00:00:00Z"));
    }
    private static EndpointPolicy optionalHostRoute() {
        return new EndpointPolicy(new EndpointId("OPTIONAL"), ClientVerificationRequirement.REQUIRED, ClientVerificationMode.IGNORE, SubjectAuthencationRequirement.HOST_SESSION_OPTIONAL, Set.of("read"), Set.of("cloud-api"), forwarding(), EndpointStatus.ENABLED);
    }
    private static EndpointPolicy signedRoute() {
        return new EndpointPolicy(new EndpointId("SIGNED"), ClientVerificationRequirement.REQUIRED, ClientVerificationMode.REQUEST_SIGNATURE, SubjectAuthencationRequirement.IGNORE, Set.of(), Set.of(), forwarding(), EndpointStatus.ENABLED);
    }
    private static CredentialForwarding forwarding() {
        return new CredentialForwarding(
                AuthorizationHeaderForwardingMode.STRIP, CookieForwardingMode.STRIP_ALL, Set.of());
    }
}
