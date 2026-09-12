package com.cloud.gateway.interfaces.webflux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.cloud.gateway.domain.access.subject.AuthenticatedSubject;
import com.cloud.gateway.application.security.BrowserSessionVerificationOutcome;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationFailReason;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class BrowserSessionSubjectAuthenticatorTest {
    @Test
    void shouldCreateAuthenticatedOutcomeForVerifiedBrowserSession() {
        AuthenticatedSubject subject = new AuthenticatedSubject(
                SubjectType.BROWSER_SESSION, "user-1", "session-1", null,
                Set.of(), Set.of(), Instant.parse("2030-01-01T00:00:00Z"));
        BrowserSessionSubjectAuthenticator authenticator = new BrowserSessionSubjectAuthenticator(
                token -> CompletableFuture.completedFuture(
                        BrowserSessionVerificationOutcome.verified(subject)));

        SubjectAuthenticationOutcome.Authenticated authenticated = assertInstanceOf(
                SubjectAuthenticationOutcome.Authenticated.class,
                authenticator.authenticate("cookies-value").block());

        assertEquals(subject, authenticated.subject());
    }

    @Test
    void shouldRejectUnverifiedBrowserSession() {
        BrowserSessionSubjectAuthenticator authenticator = new BrowserSessionSubjectAuthenticator(
                token -> CompletableFuture.completedFuture(BrowserSessionVerificationOutcome.invalid()));

        SubjectAuthenticationOutcome result = authenticator.authenticate("invalid-cookies").block();

        assertInstanceOf(SubjectAuthenticationOutcome.Rejected.class, result);
    }

    @Test
    void shouldPreserveUnavailableVerificationReason() {
        BrowserSessionSubjectAuthenticator authenticator = new BrowserSessionSubjectAuthenticator(
                token -> CompletableFuture.completedFuture(BrowserSessionVerificationOutcome.unavailable(
                        SubjectAuthenticationFailReason.UPSTREAM_UNAVAILABLE)));

        SubjectAuthenticationOutcome.Failed result = assertInstanceOf(
                SubjectAuthenticationOutcome.Failed.class,
                authenticator.authenticate("cookies-value").block());

        assertEquals(SubjectAuthenticationFailReason.UPSTREAM_UNAVAILABLE, result.reason());
    }
}
