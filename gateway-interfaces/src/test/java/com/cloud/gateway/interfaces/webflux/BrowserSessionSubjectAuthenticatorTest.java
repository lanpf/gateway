package com.cloud.gateway.interfaces.webflux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.cloud.gateway.domain.access.AuthenticatedSubject;
import com.cloud.gateway.domain.access.SubjectAuthenticationResult;
import com.cloud.gateway.domain.access.SubjectType;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class BrowserSessionSubjectAuthenticatorTest {
    @Test
    void shouldCreateAuthenticatedResultForVerifiedBrowserSession() {
        AuthenticatedSubject subject = new AuthenticatedSubject(
                SubjectType.BROWSER_SESSION, "user-1", "session-1", null,
                Set.of(), Set.of(), Instant.parse("2030-01-01T00:00:00Z"));
        BrowserSessionSubjectAuthenticator authenticator = new BrowserSessionSubjectAuthenticator(
                token -> CompletableFuture.completedFuture(Optional.of(subject)));

        SubjectAuthenticationResult.Authenticated authenticated = assertInstanceOf(
                SubjectAuthenticationResult.Authenticated.class,
                authenticator.authenticate("cookie-value").block());

        assertEquals(subject, authenticated.subject());
    }

    @Test
    void shouldRejectUnverifiedBrowserSession() {
        BrowserSessionSubjectAuthenticator authenticator = new BrowserSessionSubjectAuthenticator(
                token -> CompletableFuture.completedFuture(Optional.empty()));

        SubjectAuthenticationResult result = authenticator.authenticate("invalid-cookie").block();

        assertInstanceOf(SubjectAuthenticationResult.Rejected.class, result);
    }
}
