package com.cloud.gateway.interfaces.webflux;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.gateway.application.ingress.IngressAccessCommandService;

import com.cloud.gateway.domain.access.IngressAccessDenyReason;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.SubjectAuthencationRequirement;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.endpoint.EndpointStatus;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpCookie;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;
import reactor.core.Disposable;
import reactor.util.context.Context;

class IngressAccessGrantedContextGlobalFilterSubjectAuthenticationTest {
    private final SubjectAuthenticator<String> browserAuthenticator = credential ->
            Mono.just(SubjectAuthenticationOutcome.rejected());

    @Test
    void shouldUseHostAuthenticatorWhenOptionalAuthenticationIsPresent() {
        SubjectAuthenticator<Authentication> hostAuthenticator = credential ->
                Mono.just(SubjectAuthenticationOutcome.rejected());
        TrustedRequestContextGlobalFilter filter = filter(hostAuthenticator);
        Authentication authentication = new TestingAuthenticationToken("principal", "client");

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
                IngressAccessDenyReason.SUBJECT_AUTHENTICATION_UPSTREAM_UNAVAILABLE));
        assertEquals(HttpStatus.BAD_GATEWAY, TrustedRequestContextGlobalFilter.status(
                IngressAccessDenyReason.SUBJECT_AUTHENTICATION_RESPONSE_INVALID));
    }

    @Test
    void shouldPreserveSubscriberContextAndLazinessInSubjectAdapter() {
        AtomicBoolean invoked = new AtomicBoolean();
        TrustedRequestContextGlobalFilter filter = filter(authentication -> {
            invoked.set(true);
            return Mono.deferContextual(context -> {
                assertEquals("request-context", context.get("test-context"));
                return Mono.just(SubjectAuthenticationOutcome.rejected());
            });
        });
        Mono<SubjectAuthenticationOutcome> authentication = filter.authenticateSubject(exchange(), optionalHostRoute(),
                new TestingAuthenticationToken("principal", "client"));
        assertFalse(invoked.get());
        assertInstanceOf(SubjectAuthenticationOutcome.Rejected.class,
                authentication.contextWrite(Context.of("test-context", "request-context")).block());
        assertTrue(invoked.get());
    }

    @Test
    void shouldPropagateCancellationToSubjectAdapter() {
        AtomicBoolean cancelled = new AtomicBoolean();
        TrustedRequestContextGlobalFilter filter = filter(authentication ->
                Mono.<SubjectAuthenticationOutcome>never().doOnCancel(() -> cancelled.set(true)));
        Disposable subscription = filter.authenticateSubject(exchange(), optionalHostRoute(),
                new TestingAuthenticationToken("principal", "client")).subscribe();
        assertFalse(cancelled.get());
        subscription.dispose();
        assertTrue(cancelled.get());
    }

    @Test
    void shouldAdaptBrowserCookieLazilyWithSubscriberContext() {
        AtomicBoolean invoked = new AtomicBoolean();
        TrustedRequestContextGlobalFilter filter = new TrustedRequestContextGlobalFilter(
                new IngressAccessCommandService(null, null, null, null),
                host -> Mono.error(new AssertionError("host must not run")), cookie -> {
                    invoked.set(true);
                    assertEquals("browser-client", cookie);
                    return Mono.deferContextual(context -> {
                        assertEquals("browser-context", context.get("test-context"));
                        return Mono.just(SubjectAuthenticationOutcome.rejected());
                    });
                }, null, null);
        EndpointPolicy route = new EndpointPolicy(new EndpointId("BROWSER"), ClientVerificationRequirement.FORBIDDEN,
                ClientVerificationMode.IGNORE, SubjectAuthencationRequirement.BROWSER_SESSION_REQUIRED, Set.of(), Set.of(),
                CredentialForwarding.stripAll(), EndpointStatus.ENABLED);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/browser")
                .cookie(new HttpCookie("BROWSER_SESSION", "browser-client")));
        Mono<SubjectAuthenticationOutcome> authentication = filter.authenticateSubject(exchange, route, null);
        assertFalse(invoked.get());
        assertInstanceOf(SubjectAuthenticationOutcome.Rejected.class,
                authentication.contextWrite(Context.of("test-context", "browser-context")).block());
        assertTrue(invoked.get());
        assertInstanceOf(SubjectAuthenticationOutcome.Absent.class,
                filter.authenticateSubject(exchange(), route, null).block());
    }

    private TrustedRequestContextGlobalFilter filter(SubjectAuthenticator<Authentication> hostAuthenticator) {
        return new TrustedRequestContextGlobalFilter(
                new IngressAccessCommandService(null, null, null, null),
                hostAuthenticator, browserAuthenticator, null, null);
    }

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/optional"));
    }

    private static EndpointPolicy optionalHostRoute() {
        return new EndpointPolicy(new EndpointId("OPTIONAL"), ClientVerificationRequirement.FORBIDDEN, ClientVerificationMode.IGNORE, SubjectAuthencationRequirement.HOST_SESSION_OPTIONAL, Set.of(), Set.of("cloud-api"), CredentialForwarding.stripAll(), EndpointStatus.ENABLED);
    }
}
