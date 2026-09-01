package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.domain.access.AuthenticatedSubject;
import com.cloud.gateway.domain.access.SubjectAuthenticationResult;
import com.cloud.gateway.domain.access.SubjectType;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

public final class HostSessionSubjectAuthenticator implements SubjectAuthenticator<Authentication> {
    public static final String SUBJECT_CLAIM = "sub";
    public static final String SESSION_ID_CLAIM = "session_id";
    public static final String ISSUER_CLAIM = "iss";
    public static final String EXPIRATION_CLAIM = "exp";
    public static final String AUDIENCE_CLAIM = "aud";
    public static final String SCOPE_ALIAS_CLAIM = "scp";

    @Override
    public Mono<SubjectAuthenticationResult> authenticate(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Mono.just(SubjectAuthenticationResult.rejected());
        }
        Map<String, Object> attributes = tokenAttributes(authentication);
        String subjectId = text(attributes.get(SUBJECT_CLAIM));
        String sessionId = text(attributes.get(SESSION_ID_CLAIM));
        String issuer = text(attributes.get(ISSUER_CLAIM));
        Instant expiresAt = instant(attributes.get(EXPIRATION_CLAIM));
        if (!StringUtils.hasText(subjectId) || !StringUtils.hasText(sessionId)
                || !StringUtils.hasText(issuer) || expiresAt == null) {
            return Mono.just(SubjectAuthenticationResult.rejected());
        }
        AuthenticatedSubject subject = new AuthenticatedSubject(
                SubjectType.HOST_SESSION, subjectId, sessionId, issuer,
                strings(attributes.get(AUDIENCE_CLAIM)), scopes(attributes), expiresAt);
        return Mono.just(SubjectAuthenticationResult.authenticated(subject));
    }

    private static Map<String, Object> tokenAttributes(Authentication authentication) {
        if (authentication instanceof AbstractOAuth2TokenAuthenticationToken<?> token) {
            return token.getTokenAttributes();
        }
        return Map.of();
    }

    private static Set<String> scopes(Map<String, Object> attributes) {
        Object value = attributes.containsKey(OAuth2ParameterNames.SCOPE)
                ? attributes.get(OAuth2ParameterNames.SCOPE) : attributes.get(SCOPE_ALIAS_CLAIM);
        if (value instanceof String string) {
            return Set.of(string.split("\\s+"));
        }
        return strings(value);
    }

    private static Set<String> strings(Object value) {
        if (value instanceof Collection<?> collection) {
            Set<String> result = new HashSet<>();
            collection.forEach(item -> result.add(String.valueOf(item)));
            return Set.copyOf(result);
        }
        if (value instanceof String string && StringUtils.hasText(string)) {
            return Set.of(string);
        }
        return Set.of();
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }
        if (value instanceof String text) {
            try {
                return Instant.parse(text);
            } catch (java.time.format.DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
