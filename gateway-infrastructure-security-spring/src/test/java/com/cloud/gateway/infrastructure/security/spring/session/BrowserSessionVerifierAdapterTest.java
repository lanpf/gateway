package com.cloud.gateway.infrastructure.security.spring.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cloud.framework.core.Result;
import com.cloud.framework.core.SubjectType;
import com.cloud.gateway.application.security.BrowserSessionVerificationOutcome;
import com.cloud.gateway.domain.ingress.access.subject.AuthenticatedSubject;
import com.cloud.gateway.domain.ingress.access.subject.SubjectAuthenticationFailReason;
import com.cloud.userauth.api.authentication.VerifyBrowserSessionApiCommandOutput;
import com.cloud.userauth.api.constants.UserAuthPathApiConstants;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

class BrowserSessionVerifierAdapterTest {
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void shouldMapVerifiedPayloadToBrowserSessionSubject() {
        BrowserSessionVerificationOutcome.Verified result = assertInstanceOf(
                BrowserSessionVerificationOutcome.Verified.class,
                BrowserSessionVerifierAdapter.subject(payload(
                VerifyBrowserSessionApiCommandOutput.verified(1001L, "session-1", 1800L)),
                NOW));

        AuthenticatedSubject value = result.subject();
        assertEquals(SubjectType.BROWSER_SESSION, value.subjectType());
        assertEquals("1001", value.subjectId().value());
        assertEquals("session-1", value.sessionId().value());
        assertEquals(NOW.plusSeconds(1800), value.authenticationExpiresAt());
        assertNull(value.issuer());
        assertEquals(0, value.audiences().size());
        assertEquals(0, value.scopes().size());
    }

    @Test
    void shouldUseFixedVerificationPathFromConfiguredServiceBaseUrl() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://identity-service")
                .exchangeFunction(request -> {
                    requestedUri.set(request.url());
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"code\":\"0\",\"message\":\"SUCCESS\",\"data\":{"
                                    + "\"verified\":true,\"userId\":1001,"
                                    + "\"sessionId\":\"session-1\",\"remainingIdleTtlSeconds\":1800}}")
                            .build());
                })
                .build();
        BrowserSessionVerifierAdapter verifier = new BrowserSessionVerifierAdapter(
                webClient, Clock.fixed(NOW, ZoneOffset.UTC));

        BrowserSessionVerificationOutcome outcome = verifier.verify("client")
                .toCompletableFuture().join();

        assertInstanceOf(BrowserSessionVerificationOutcome.Verified.class, outcome);
        assertEquals(URI.create("http://identity-service"
                        + UserAuthPathApiConstants.INTERNAL_BROWSER_SESSIONS_VERIFY),
                requestedUri.get());
    }

    @Test
    void shouldNotMapUnverifiedOrIncompletePayload() {
        assertInstanceOf(BrowserSessionVerificationOutcome.Unavailable.class,
                BrowserSessionVerifierAdapter.subject(null, NOW));
        assertInstanceOf(BrowserSessionVerificationOutcome.Invalid.class, BrowserSessionVerifierAdapter.subject(
                payload(new VerifyBrowserSessionApiCommandOutput(
                        false, null, null, null)), NOW));
        assertInstanceOf(BrowserSessionVerificationOutcome.Unavailable.class, BrowserSessionVerifierAdapter.subject(
                payload(new VerifyBrowserSessionApiCommandOutput(
                        true, null, "session-1", 1800L)), NOW));
        assertInstanceOf(BrowserSessionVerificationOutcome.Unavailable.class, BrowserSessionVerifierAdapter.subject(
                payload(new VerifyBrowserSessionApiCommandOutput(
                        true, 1001L, " ", 1800L)), NOW));
        assertInstanceOf(BrowserSessionVerificationOutcome.Unavailable.class, BrowserSessionVerifierAdapter.subject(
                payload(new VerifyBrowserSessionApiCommandOutput(
                        true, 1001L, "session-1", 0L)), NOW));
    }

    @Test
    void shouldReportDependencyUnavailableForTransportFailure() {
        WebClient webClient = WebClient.builder().exchangeFunction(request -> Mono.error(
                new WebClientRequestException(new IOException("connection refused"),
                        HttpMethod.POST, URI.create("http://user-auth/internal"), HttpHeaders.EMPTY))).build();
        BrowserSessionVerifierAdapter verifier = new BrowserSessionVerifierAdapter(
                webClient, Clock.fixed(NOW, ZoneOffset.UTC));

        BrowserSessionVerificationOutcome.Unavailable result = assertInstanceOf(
                BrowserSessionVerificationOutcome.Unavailable.class,
                verifier.verify("client").toCompletableFuture().join());

        assertEquals(SubjectAuthenticationFailReason.UPSTREAM_UNAVAILABLE,
                result.reason());
    }

    @Test
    void shouldDistinguishInvalidCredentialFromDependencyFailure() {
        WebClient invalidClient = WebClient.builder().exchangeFunction(request -> Mono.error(
                WebClientResponseException.create(401, "Unauthorized", HttpHeaders.EMPTY, null, null))).build();
        WebClient unavailableClient = WebClient.builder().exchangeFunction(request -> Mono.error(
                WebClientResponseException.create(503, "Unavailable", HttpHeaders.EMPTY, null, null))).build();

        BrowserSessionVerificationOutcome invalid = verifier(invalidClient).verify("client")
                .toCompletableFuture().join();
        BrowserSessionVerificationOutcome.Unavailable unavailable = assertInstanceOf(
                BrowserSessionVerificationOutcome.Unavailable.class,
                verifier(unavailableClient).verify("client").toCompletableFuture().join());

        assertInstanceOf(BrowserSessionVerificationOutcome.Invalid.class, invalid);
        assertEquals(SubjectAuthenticationFailReason.UPSTREAM_UNAVAILABLE,
                unavailable.reason());
    }

    @Test
    void shouldKeepOutcomeStableAcrossDegradedAndRecoveryCyclesOnSameInstance() {
        AtomicReference<Mono<ClientResponse>> nextResponse = new AtomicReference<>(Mono.error(
                WebClientResponseException.create(503, "Unavailable", HttpHeaders.EMPTY, null, null)));
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> nextResponse.get())
                .build();
        BrowserSessionVerifierAdapter verifier = new BrowserSessionVerifierAdapter(
                webClient, Clock.fixed(NOW, ZoneOffset.UTC));

        assertInstanceOf(BrowserSessionVerificationOutcome.Unavailable.class,
                verifier.verify("client").toCompletableFuture().join());
        assertInstanceOf(BrowserSessionVerificationOutcome.Unavailable.class,
                verifier.verify("client").toCompletableFuture().join());

        nextResponse.set(Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"code\":\"0\",\"message\":\"SUCCESS\",\"data\":{"
                        + "\"verified\":true,\"userId\":1001,"
                        + "\"sessionId\":\"session-1\",\"remainingIdleTtlSeconds\":1800}}")
                .build()));

        assertInstanceOf(BrowserSessionVerificationOutcome.Verified.class,
                verifier.verify("client").toCompletableFuture().join());
    }

    private static BrowserSessionVerifierAdapter verifier(WebClient webClient) {
        return new BrowserSessionVerifierAdapter(
                webClient, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Result<VerifyBrowserSessionApiCommandOutput> payload(
            VerifyBrowserSessionApiCommandOutput data
    ) {
        return Result.success(data);
    }
}
