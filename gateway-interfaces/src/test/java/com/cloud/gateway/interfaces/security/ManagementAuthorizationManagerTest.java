package com.cloud.gateway.interfaces.security;

import com.cloud.gateway.domain.security.ManagementAccessRules;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class ManagementAuthorizationManagerTest {
    private final ManagementAuthorizationManager manager = new ManagementAuthorizationManager(new ManagementAccessRules("gateway"));

    @Test
    void shouldRequireAuthenticatedHostTokenWithManagementScopeAndAudience() {
        assertTrue(manager.check(Mono.just(token("admin", List.of("gateway"))), null).block().isGranted());
        assertTrue(manager.check(Mono.just(token("admin", "gateway")), null).block().isGranted());
        assertFalse(manager.check(Mono.just(token("read", List.of("gateway"))), null).block().isGranted());
        assertFalse(manager.check(Mono.just(token("admin", List.of("orders"))), null).block().isGranted());
        assertFalse(manager.check(Mono.empty(), null).block().isGranted());
        Authentication notToken = new UsernamePasswordAuthenticationToken("user", "unused", List.of(new SimpleGrantedAuthority("SCOPE_admin")));
        assertFalse(manager.check(Mono.just(notToken), null).block().isGranted());
    }

    private static Authentication token(String scope, Object audiences) {
        Jwt jwt = Jwt.withTokenValue("redacted").header("alg", "RS256").subject("subject")
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(100)).claim("aud", audiences).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_" + scope)));
    }
}
