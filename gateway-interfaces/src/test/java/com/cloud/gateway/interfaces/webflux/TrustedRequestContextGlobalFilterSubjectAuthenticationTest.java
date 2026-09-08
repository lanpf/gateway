package com.cloud.gateway.interfaces.webflux;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cloud.gateway.domain.access.IngressAccessDenyReason;
import com.cloud.gateway.domain.access.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.policy.ChannelCredentialRequirement;
import com.cloud.gateway.domain.policy.ChannelRequirement;
import com.cloud.gateway.domain.policy.CredentialForwardingPolicy;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import com.cloud.gateway.domain.policy.SubjectRequirement;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

class TrustedRequestContextGlobalFilterSubjectAuthenticationTest {
    private final SubjectAuthenticator<String> browserAuthenticator = credential ->
            Mono.just(SubjectAuthenticationOutcome.rejected());

    @Test
    void shouldUseHostAuthenticatorWhenOptionalAuthenticationIsPresent() {
        SubjectAuthenticator<Authentication> hostAuthenticator = credential ->
                Mono.just(SubjectAuthenticationOutcome.rejected());
        TrustedRequestContextGlobalFilter filter = filter(hostAuthenticator);
        Authentication authentication = new TestingAuthenticationToken("principal", "credential");

        SubjectAuthenticationOutcome result = filter.authenticateSubject(
                exchange(), optionalHostRoute(), authentication).block();

        assertInstanceOf(SubjectAuthenticationOutcome.Rejected.class, result);
    }

    @Test
    void shouldSkipHostAuthenticatorWhenOptionalAuthenticationIsAbsent() {
        SubjectAuthenticator<Authentication> hostAuthenticator = credential ->
                Mono.error(new AssertionError("host authenticator不应被调用"));
        TrustedRequestContextGlobalFilter filter = filter(hostAuthenticator);

        SubjectAuthenticationOutcome result = filter.authenticateSubject(
                exchange(), optionalHostRoute(), null).block();

        assertInstanceOf(SubjectAuthenticationOutcome.Absent.class, result);
    }

    @Test
    void shouldMapBrowserSessionVerificationOutagesWithoutPretendingCredentialIsInvalid() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, TrustedRequestContextGlobalFilter.status(
                IngressAccessDenyReason.SUBJECT_AUTHENTICATION_DEPENDENCY_UNAVAILABLE));
        assertEquals(HttpStatus.BAD_GATEWAY, TrustedRequestContextGlobalFilter.status(
                IngressAccessDenyReason.SUBJECT_AUTHENTICATION_RESPONSE_INVALID));
    }

    private TrustedRequestContextGlobalFilter filter(SubjectAuthenticator<Authentication> hostAuthenticator) {
        return new TrustedRequestContextGlobalFilter(
                null, null, hostAuthenticator, browserAuthenticator, null, null);
    }

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/optional"));
    }

    private static IngressRouteAccessPolicy optionalHostRoute() {
        return new IngressRouteAccessPolicy(new EndpointId("OPTIONAL"), ChannelCredentialRequirement.IGNORE,
                ChannelRequirement.FORBIDDEN, SubjectRequirement.HOST_SESSION_OPTIONAL,
                Set.of(), Set.of("cloud-api"), CredentialForwardingPolicy.stripAll(), true);
    }
}
