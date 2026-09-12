package com.cloud.gateway.interfaces.security;

import com.cloud.gateway.application.policy.PolicySnapshotProvider;
import com.cloud.gateway.application.policy.VersionedPolicySnapshot;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.interfaces.webflux.GatewayRequestAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.WebFilterChainProxy;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

/** Real production security beans and filter execution, without Boot, sockets or middleware. */
class GatewaySecurityWebFluxBeansTest {
    private static final String ADMIN_PATH = "/admin/gateway/snapshot";
    private static final Duration WAIT = Duration.ofSeconds(5);

    @Test
    void shouldRejectManagementRequestWithoutTokenForBothVerificationAdapters() {
        for (TokenFormat format : TokenFormat.values()) {
            try (SecurityContext fixture = new SecurityContext(format)) {
                assertRejected(fixture.request(ADMIN_PATH, null), HttpStatus.UNAUTHORIZED);
                assertEquals(0, fixture.verifications.get());
            }
        }
    }

    @Test
    void shouldRejectManagementRequestWithWrongScopeOrAudienceForBothVerificationAdapters() {
        for (TokenFormat format : TokenFormat.values()) {
            try (SecurityContext fixture = new SecurityContext(format)) {
                assertRejected(fixture.request(ADMIN_PATH, "Bearer wrong-scope"), HttpStatus.FORBIDDEN);
                assertRejected(fixture.request(ADMIN_PATH, "Bearer wrong-audience"), HttpStatus.FORBIDDEN);
                assertEquals(2, fixture.verifications.get());
            }
        }
    }

    @Test
    void shouldAllowManagementRequestWithRequiredScopeAndAudienceForBothVerificationAdapters() {
        for (TokenFormat format : TokenFormat.values()) {
            try (SecurityContext fixture = new SecurityContext(format)) {
                Call call = fixture.request(ADMIN_PATH, "Bearer valid");
                assertEquals(HttpStatus.NO_CONTENT, call.status());
                assertTrue(call.terminalReached());
                assertEquals(1, fixture.verifications.get());
            }
        }
    }

    @Test
    void shouldSkipBearerForBrowserAndIgnoredEvidenceButAuthenticateHostThroughRealFilterChain() {
        for (TokenFormat format : TokenFormat.values()) {
            try (SecurityContext fixture = new SecurityContext(format)) {
                for (String path : List.of("/browser", "/ignored")) {
                    Call call = fixture.request(path, "Bearer invalid token");
                    assertEquals(HttpStatus.NO_CONTENT, call.status());
                    assertTrue(call.terminalReached());
                }
                assertEquals(0, fixture.verifications.get(), "non-host requests must not reach a Bearer verifier");
                Call host = fixture.request("/host", "Bearer valid");
                assertEquals(HttpStatus.NO_CONTENT, host.status());
                assertTrue(host.terminalReached());
                assertEquals(1, fixture.verifications.get());
                assertRejected(fixture.request("/host", "Bearer invalid token"), HttpStatus.UNAUTHORIZED);
                assertEquals(1, fixture.verifications.get(), "malformed host evidence must reject before decoding");
            }
        }
    }

    private static void assertRejected(Call call, HttpStatus status) {
        assertEquals(status, call.status());
        assertFalse(call.terminalReached(), "rejected requests must never reach the terminal handler");
    }

    private enum TokenFormat { JWT, REFERENCE }

    private record Call(HttpStatusCode status, boolean terminalReached) { }

    private static final class SecurityContext implements AutoCloseable {
        private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        private final AtomicInteger verifications = new AtomicInteger();
        private final IngressAccessSnapshot snapshot = snapshot();
        private final WebFilterChainProxy filter;

        private SecurityContext(TokenFormat format) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("security-contract",
                    Map.of("gateway.security.admin-audience", "gateway")));
            context.registerBean(PolicySnapshotProvider.class, () -> () -> new VersionedPolicySnapshot(snapshot, "revision"));
            if (format == TokenFormat.JWT) {
                context.registerBean(ReactiveJwtDecoder.class, () -> token -> Mono.fromSupplier(() -> {
                    verifications.incrementAndGet();
                    return jwt(token);
                }));
            } else {
                context.registerBean(ReactiveOpaqueTokenIntrospector.class, () -> token -> Mono.fromSupplier(() -> {
                    verifications.incrementAndGet();
                    return new DefaultOAuth2AuthenticatedPrincipal("subject", claims(token),
                            List.of(new SimpleGrantedAuthority("SCOPE_" + scope(token))));
                }));
            }
            context.register(GatewaySecurityWebFluxBeans.class);
            try {
                context.refresh();
                assertEquals(1, context.getBeansOfType(SecurityWebFilterChain.class).size());
                filter = context.getBean(WebFilterChainProxy.class);
            } catch (RuntimeException | Error failure) {
                context.close();
                throw failure;
            }
        }

        private Call request(String path, String authorization) {
            MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get(path);
            if (authorization != null) {
                request.header(HttpHeaders.AUTHORIZATION, authorization);
            }
            MockServerWebExchange exchange = MockServerWebExchange.from(request.build());
            Endpoint endpoint = snapshot.content().endpointRegistry().definitions().stream()
                    .filter(value -> value.matchers().get(0).pathPattern().equals(path)).findFirst().orElseThrow();
            exchange.getAttributes().put(GatewayRequestAttributes.SNAPSHOT, snapshot);
            exchange.getAttributes().put(GatewayRequestAttributes.ENDPOINT, endpoint);
            AtomicBoolean reached = new AtomicBoolean();
            filter.filter(exchange, forwarded -> {
                reached.set(true);
                forwarded.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                return forwarded.getResponse().setComplete();
            }).block(WAIT);
            return new Call(exchange.getResponse().getStatusCode(), reached.get());
        }

        @Override
        public void close() {
            context.close();
        }

        private static Jwt jwt(String token) {
            return Jwt.withTokenValue(token).header("alg", "RS256")
                    .claims(attributes -> attributes.putAll(claims(token))).build();
        }

        private static Map<String, Object> claims(String token) {
            return Map.of("sub", "subject", "aud", List.of("wrong-audience".equals(token) ? "orders" : "gateway"),
                    "scope", scope(token), "iat", Instant.EPOCH, "exp", Instant.parse("2100-01-01T00:00:00Z"));
        }

        private static String scope(String token) {
            return "wrong-scope".equals(token) ? "read" : "admin";
        }

        private static IngressAccessSnapshot snapshot() {
            Map<String, SubjectAuthenticationRequirement> requirements = Map.of(
                    ADMIN_PATH, SubjectAuthenticationRequirement.HOST_SESSION_REQUIRED,
                    "/host", SubjectAuthenticationRequirement.HOST_SESSION_OPTIONAL,
                    "/browser", SubjectAuthenticationRequirement.BROWSER_SESSION_REQUIRED,
                    "/ignored", SubjectAuthenticationRequirement.IGNORE);
            Map<EndpointId, EndpointPolicy> policies = new LinkedHashMap<>();
            List<Endpoint> endpoints = requirements.entrySet().stream().map(entry -> {
                EndpointId id = new EndpointId("ENDPOINT." + entry.getValue().name());
                boolean admin = entry.getKey().equals(ADMIN_PATH);
                boolean host = admin || entry.getValue() == SubjectAuthenticationRequirement.HOST_SESSION_OPTIONAL;
                policies.put(id, new EndpointPolicy(id, ClientVerificationRequirement.OPTIONAL, ClientVerificationMode.IGNORE,
                        entry.getValue(), admin ? Set.of("admin") : Set.of(), host ? Set.of("gateway") : Set.of(),
                        CredentialForwarding.stripAll(), EndpointStatus.ENABLED));
                return new Endpoint(id, "gateway", List.of(new EndpointMatcher("GET", entry.getKey())),
                        admin ? GatewayRequestAttributes.LOCAL_ROUTE_ID : "route");
            }).toList();
            return new IngressAccessSnapshot(1,
                    new IngressAccessContent(new EndpointRegistry(endpoints), Map.of(), Map.of(), policies), "hash", Instant.EPOCH);
        }
    }
}
