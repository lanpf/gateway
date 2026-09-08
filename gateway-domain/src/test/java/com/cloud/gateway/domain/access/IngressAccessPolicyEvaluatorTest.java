package com.cloud.gateway.domain.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cloud.gateway.domain.policy.AuthorizationHeaderPolicy;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.ChannelCredentialRequirement;
import com.cloud.gateway.domain.policy.ChannelRequirement;
import com.cloud.gateway.domain.policy.ClientAppStatus;
import com.cloud.gateway.domain.policy.ClientAppId;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.policy.CredentialForwardingPolicy;
import com.cloud.gateway.domain.policy.IngressChannelAccessPolicy;
import com.cloud.gateway.domain.policy.IngressClientAppPolicy;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import com.cloud.gateway.domain.policy.SubjectRequirement;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IngressAccessPolicyEvaluatorTest {
    private final IngressAccessPolicyEvaluator evaluator = new IngressAccessPolicyEvaluator();

    @Test
    void shouldAllowWhenClientChannelSubjectAudienceAndScopeAllMatch() {
        IngressAccessDecision decision = evaluator.evaluate(
                7, new ChannelCode("DIRECT"), client(), channel(), route(),
                null, false,
                ChannelCredentialAuthenticationOutcome.absent(),
                authenticated(subject()));

        IngressAccessDecision.Allowed allowed = assertInstanceOf(IngressAccessDecision.Allowed.class, decision);
        assertEquals("mini-program", allowed.trustedRequestContext().clientContext().clientAppId().value());
        assertEquals("user-1", allowed.trustedRequestContext().subjectContext().subjectId());
        assertEquals(7, allowed.trustedRequestContext().policySnapshotVersion());
    }

    @Test
    void shouldDenyWhenRequiredAudienceIsMissing() {
        AuthenticatedSubject subject = new AuthenticatedSubject(SubjectType.HOST_SESSION, "user-1", "session-1",
                "issuer", Set.of("another-api"), Set.of("read"), Instant.parse("2030-01-01T00:00:00Z"));

        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(7, new ChannelCode("DIRECT"), client(), channel(), route(),
                        null, false,
                        ChannelCredentialAuthenticationOutcome.absent(),
                        authenticated(subject)));

        assertEquals(IngressAccessDenyReason.AUDIENCE_NOT_ALLOWED, denied.reason());
    }

    @Test
    void shouldRejectChannelForForbiddenRoute() {
        IngressRouteAccessPolicy route = new IngressRouteAccessPolicy(new EndpointId("PUBLIC"), ChannelCredentialRequirement.IGNORE,
                ChannelRequirement.FORBIDDEN, SubjectRequirement.IGNORE, Set.of(), Set.of(), forwarding(), true);

        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(1, new ChannelCode("DIRECT"), client(), channel(), route,
                        null, false,
                        ChannelCredentialAuthenticationOutcome.absent(),
                        SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.CHANNEL_NOT_PERMITTED_FOR_ENDPOINT, denied.reason());
    }

    @Test
    void shouldAllowOptionalHostSessionOnlyWhenCredentialIsAbsent() {
        IngressRouteAccessPolicy route = optionalHostRoute();

        IngressAccessDecision decision = evaluator.evaluate(
                7, new ChannelCode("DIRECT"), client(), channel(), route,
                        null, false,
                        ChannelCredentialAuthenticationOutcome.absent(),
                        SubjectAuthenticationOutcome.absent());

        IngressAccessDecision.Allowed allowed = assertInstanceOf(IngressAccessDecision.Allowed.class, decision);
        assertNull(allowed.trustedRequestContext().subjectContext());
    }

    @Test
    void shouldRejectInvalidCredentialForOptionalHostSession() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(7, new ChannelCode("DIRECT"), client(), channel(), optionalHostRoute(),
                        null, false,
                        ChannelCredentialAuthenticationOutcome.absent(),
                        SubjectAuthenticationOutcome.rejected()));

        assertEquals(IngressAccessDenyReason.SUBJECT_AUTHENTICATION_FAILED, denied.reason());
    }

    @Test
    void shouldPreserveSubjectAuthenticationDependencyFailure() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(7, new ChannelCode("DIRECT"), client(), channel(), route(),
                        null, false,
                        ChannelCredentialAuthenticationOutcome.absent(),
                        SubjectAuthenticationOutcome.unavailable(
                                SubjectAuthenticationUnavailableReason.DEPENDENCY_UNAVAILABLE)));

        assertEquals(IngressAccessDenyReason.SUBJECT_AUTHENTICATION_DEPENDENCY_UNAVAILABLE,
                denied.reason());
    }

    @Test
    void shouldRequireMissingChannelCredential() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(7, new ChannelCode("DIRECT"), client(), channel(), signedRoute(),
                        null, false,
                        ChannelCredentialAuthenticationOutcome.absent(),
                        SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.CHANNEL_CREDENTIAL_REQUIRED, denied.reason());
    }

    @Test
    void shouldRejectInvalidChannelCredential() {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(7, new ChannelCode("DIRECT"), client(), channel(), signedRoute(),
                        null, false,
                        ChannelCredentialAuthenticationOutcome.rejected(),
                        SubjectAuthenticationOutcome.absent()));

        assertEquals(IngressAccessDenyReason.CHANNEL_CREDENTIAL_INVALID, denied.reason());
    }

    @Test
    void shouldRequireAllowedOriginForUnsafeBrowserSessionRequest() {
        IngressAccessDecision.Denied missing = assertInstanceOf(
                IngressAccessDecision.Denied.class,
                evaluator.evaluate(
                        7, new ChannelCode("DIRECT"), client(), channel(), browserRoute(),
                        null, true,
                        ChannelCredentialAuthenticationOutcome.absent(),
                        authenticated(browserSubject())));
        IngressAccessDecision.Denied mismatched = assertInstanceOf(
                IngressAccessDecision.Denied.class,
                evaluator.evaluate(
                        7, new ChannelCode("DIRECT"), client(), channel(), browserRoute(),
                        "https://evil.example.com", true,
                        ChannelCredentialAuthenticationOutcome.absent(),
                        authenticated(browserSubject())));

        assertEquals(IngressAccessDenyReason.BROWSER_ORIGIN_NOT_ALLOWED, missing.reason());
        assertEquals(IngressAccessDenyReason.BROWSER_ORIGIN_NOT_ALLOWED, mismatched.reason());
    }

    @Test
    void shouldAllowMatchingOriginForUnsafeBrowserSessionRequest() {
        IngressAccessDecision decision = evaluator.evaluate(
                7, new ChannelCode("DIRECT"), client(), channel(), browserRoute(),
                "https://h5.example.com", true,
                ChannelCredentialAuthenticationOutcome.absent(),
                authenticated(browserSubject()));

        assertInstanceOf(IngressAccessDecision.Allowed.class, decision);
    }

    @Test
    void shouldNotRequireOriginForSafeBrowserSessionRequest() {
        IngressAccessDecision decision = evaluator.evaluate(
                7, new ChannelCode("DIRECT"), client(), channel(), browserRoute(),
                null, false,
                ChannelCredentialAuthenticationOutcome.absent(),
                authenticated(browserSubject()));

        assertInstanceOf(IngressAccessDecision.Allowed.class, decision);
    }

    private static IngressClientAppPolicy client() {
        return new IngressClientAppPolicy(
                new ClientAppId("mini-program"),
                ClientAppStatus.ENABLED,
                "WECHAT_MINI_PROGRAM",
                Set.of("https://h5.example.com"));
    }
    private static IngressChannelAccessPolicy channel() {
        return new IngressChannelAccessPolicy(new ChannelCode("DIRECT"), Set.of(new ClientAppId("mini-program")));
    }
    private static IngressRouteAccessPolicy route() {
        return new IngressRouteAccessPolicy(new EndpointId("AUTHENTICATED"), ChannelCredentialRequirement.IGNORE,
                ChannelRequirement.REQUIRED, SubjectRequirement.HOST_SESSION_REQUIRED,
                Set.of("read"), Set.of("cloud-api"), forwarding(), true);
    }
    private static AuthenticatedSubject subject() {
        return new AuthenticatedSubject(SubjectType.HOST_SESSION, "user-1", "session-1", "issuer",
                Set.of("cloud-api"), Set.of("read"), Instant.parse("2030-01-01T00:00:00Z"));
    }
    private static SubjectAuthenticationOutcome authenticated(AuthenticatedSubject subject) {
        return SubjectAuthenticationOutcome.authenticated(subject);
    }
    private static IngressRouteAccessPolicy browserRoute() {
        return new IngressRouteAccessPolicy(
                new EndpointId("BROWSER"),
                ChannelCredentialRequirement.IGNORE,
                ChannelRequirement.REQUIRED,
                SubjectRequirement.BROWSER_SESSION_REQUIRED,
                Set.of(), Set.of(), forwarding(), true);
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
    private static IngressRouteAccessPolicy optionalHostRoute() {
        return new IngressRouteAccessPolicy(new EndpointId("OPTIONAL"), ChannelCredentialRequirement.IGNORE,
                ChannelRequirement.REQUIRED, SubjectRequirement.HOST_SESSION_OPTIONAL,
                Set.of("read"), Set.of("cloud-api"), forwarding(), true);
    }
    private static IngressRouteAccessPolicy signedRoute() {
        return new IngressRouteAccessPolicy(new EndpointId("SIGNED"), ChannelCredentialRequirement.REQUEST_SIGNATURE,
                ChannelRequirement.REQUIRED, SubjectRequirement.IGNORE,
                Set.of(), Set.of(), forwarding(), true);
    }
    private static CredentialForwardingPolicy forwarding() {
        return new CredentialForwardingPolicy(
                AuthorizationHeaderPolicy.STRIP, CookieForwardingMode.STRIP_ALL, Set.of());
    }
}
