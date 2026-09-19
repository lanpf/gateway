package com.cloud.gateway.interfaces.security;

import com.cloud.framework.core.SubjectType;
import com.cloud.gateway.domain.security.ManagementAccessRules;
import com.cloud.gateway.interfaces.webflux.HostSessionSubjectAuthenticator;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/** Converts Spring token attributes into the common domain authorization vocabulary. */
@RequiredArgsConstructor
public final class ManagementAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {
    private static final String SCOPE_PREFIX = "SCOPE_";
    private final ManagementAccessRules rules;

    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication.map(value -> new AuthorizationDecision(allows(value)))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private boolean allows(Authentication authentication) {
        if (!authentication.isAuthenticated() || !(authentication instanceof AbstractOAuth2TokenAuthenticationToken<?> token)) {
            return false;
        }
        Map<String, Object> attributes = token.getTokenAttributes();
        if (!hasHostSessionClaims(attributes)) {
            return false;
        }
        Set<String> scopes = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(value -> value.startsWith(SCOPE_PREFIX)).map(value -> value.substring(SCOPE_PREFIX.length()))
                .collect(Collectors.toUnmodifiableSet());
        Object attribute = attributes.get(JwtClaimNames.AUD);
        Set<String> audiences;
        if (attribute instanceof Collection<?> collection) {
            audiences = collection.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
        } else if (attribute instanceof String value) {
            audiences = Set.of(value);
        } else {
            audiences = Set.of();
        }
        return rules.allows(SubjectType.HOST_SESSION, scopes, audiences);
    }

    /**
     * Management runs on the Host session chain only. Requiring the Host session claims keeps the
     * domain rule honest instead of asserting the subject type: a token without them is not a host
     * session and never becomes a management subject.
     */
    private static boolean hasHostSessionClaims(Map<String, Object> attributes) {
        return StringUtils.hasText(text(attributes.get(HostSessionSubjectAuthenticator.SUBJECT_CLAIM)))
                && StringUtils.hasText(text(attributes.get(HostSessionSubjectAuthenticator.SESSION_ID_CLAIM)))
                && StringUtils.hasText(text(attributes.get(HostSessionSubjectAuthenticator.ISSUER_CLAIM)))
                && attributes.get(HostSessionSubjectAuthenticator.EXPIRATION_CLAIM) != null;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
