package com.cloud.gateway.interfaces.webflux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.cloud.gateway.domain.access.AuthenticatedSubject;
import com.cloud.gateway.application.security.BrowserSessionVerificationOutcome;
import com.cloud.gateway.domain.access.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.access.SubjectAuthenticationUnavailableReason;
import com.cloud.gateway.domain.access.SubjectType;
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
                authenticator.authenticate("cookie-value").block());

        assertEquals(subject, authenticated.subject());
    }

    @Test
    void shouldRejectUnverifiedBrowserSession() {
        BrowserSessionSubjectAuthenticator authenticator = new BrowserSessionSubjectAuthenticator(
                token -> CompletableFuture.completedFuture(BrowserSessionVerificationOutcome.invalid()));

        SubjectAuthenticationOutcome result = authenticator.authenticate("invalid-cookie").block();

        assertInstanceOf(SubjectAuthenticationOutcome.Rejected.class, result);
    }

    @Test
    void shouldPreserveUnavailableVerificationReason() {
        BrowserSessionSubjectAuthenticator authenticator = new BrowserSessionSubjectAuthenticator(
                token -> CompletableFuture.completedFuture(BrowserSessionVerificationOutcome.unavailable(
                        SubjectAuthenticationUnavailableReason.DEPENDENCY_UNAVAILABLE)));

        SubjectAuthenticationOutcome.Unavailable result = assertInstanceOf(
                SubjectAuthenticationOutcome.Unavailable.class,
                authenticator.authenticate("cookie-value").block());

        assertEquals(SubjectAuthenticationUnavailableReason.DEPENDENCY_UNAVAILABLE, result.reason());
    }
}
