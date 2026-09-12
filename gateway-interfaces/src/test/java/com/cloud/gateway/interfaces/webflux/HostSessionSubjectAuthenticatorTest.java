package com.cloud.gateway.interfaces.webflux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

class HostSessionSubjectAuthenticatorTest {
    private final HostSessionSubjectAuthenticator authenticator = new HostSessionSubjectAuthenticator();

    @Test
    void shouldCreateHostSessionSubjectFromAuthenticatedToken() {
        Instant expiresAt = Instant.parse("2030-01-01T00:00:00Z");
        TestTokenAuthentication authentication = new TestTokenAuthentication(Map.of(
                HostSessionSubjectAuthenticator.SUBJECT_CLAIM, "user-1",
                HostSessionSubjectAuthenticator.SESSION_ID_CLAIM, "session-1",
                HostSessionSubjectAuthenticator.ISSUER_CLAIM, "https://issuer.example",
                HostSessionSubjectAuthenticator.AUDIENCE_CLAIM, List.of("cloud-api"),
                OAuth2ParameterNames.SCOPE, "read write",
                HostSessionSubjectAuthenticator.EXPIRATION_CLAIM, expiresAt));

        SubjectAuthenticationOutcome.Authenticated authenticated = assertInstanceOf(
                SubjectAuthenticationOutcome.Authenticated.class,
                authenticator.authenticate(authentication).block());

        assertEquals("user-1", authenticated.subject().subjectId());
        assertEquals("session-1", authenticated.subject().sessionId());
        assertEquals(List.of("cloud-api"), authenticated.subject().audiences().stream().toList());
    }

    @Test
    void shouldRejectUnsupportedAuthentication() {
        SubjectAuthenticationOutcome result = authenticator
                .authenticate(new TestingAuthenticationToken("principal", "client"))
                .block();

        assertInstanceOf(SubjectAuthenticationOutcome.Rejected.class, result);
    }

    private static final class TestTokenAuthentication
            extends AbstractOAuth2TokenAuthenticationToken<OAuth2AccessToken> {
        @Getter private final Map<String, Object> tokenAttributes;

        private TestTokenAuthentication(Map<String, Object> attributes) {
            super(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token",
                    Instant.parse("2029-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z")));
            this.tokenAttributes = Map.copyOf(attributes);
            setAuthenticated(true);
        }
    }
}
