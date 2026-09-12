package com.cloud.gateway.interfaces.security;

import static org.junit.jupiter.api.Assertions.*;

import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.interfaces.webflux.GatewayRequestAttributes;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ServerWebExchange;

class PolicyAwareBearerTokenConverterTest {
    @Test
    void shouldIgnoreEvenMalformedBearerForIgnoredOrBrowserSubject() {
        for (SubjectAuthenticationRequirement requirement : List.of(SubjectAuthenticationRequirement.IGNORE, SubjectAuthenticationRequirement.BROWSER_SESSION_REQUIRED)) {
            assertNull(new PolicyAwareBearerTokenConverter()
                    .convert(exchange(requirement, "Bearer invalid token", true)).block());
        }
    }

    @Test
    void shouldAuthenticateHostBearerAndUseOnlyPinnedRequestAttributes() {
        for (SubjectAuthenticationRequirement requirement : List.of(SubjectAuthenticationRequirement.HOST_SESSION_REQUIRED, SubjectAuthenticationRequirement.HOST_SESSION_OPTIONAL)) {
            Authentication authentication = new PolicyAwareBearerTokenConverter()
                    .convert(exchange(requirement, "Bearer access-token", true)).block();
            assertEquals("access-token", assertInstanceOf(BearerTokenAuthenticationToken.class, authentication).getToken());
        }
    }

    @Test
    void shouldKeepDefaultVerificationWhenSnapshotAttributesAreAbsent() {
        assertThrows(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,
                () -> new PolicyAwareBearerTokenConverter()
                        .convert(exchange(SubjectAuthenticationRequirement.IGNORE, "Bearer invalid token", false)).block());
    }

    private static ServerWebExchange exchange(SubjectAuthenticationRequirement requirement, String authorization, boolean pinned) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        ServerHttpRequest request = (ServerHttpRequest) Proxy.newProxyInstance(ServerHttpRequest.class.getClassLoader(),
                new Class<?>[]{ServerHttpRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getHeaders" -> headers;
                    case "getMethod" -> HttpMethod.GET;
                    case "getQueryParams" -> new LinkedMultiValueMap<String, String>();
                    default -> throw new AssertionError("Unexpected request method " + method.getName());
                });
        Map<String, Object> attributes = new HashMap<>();
        if (pinned) {
            Endpoint endpoint = new Endpoint(new EndpointId("TEST.ENDPOINT"), "test-service",
                    List.of(new EndpointMatcher("GET", "/test")), "test-route");
            boolean host = requirement == SubjectAuthenticationRequirement.HOST_SESSION_REQUIRED
                    || requirement == SubjectAuthenticationRequirement.HOST_SESSION_OPTIONAL;
            EndpointPolicy policy = new EndpointPolicy(endpoint.endpointId(), ClientVerificationRequirement.OPTIONAL, ClientVerificationMode.IGNORE, requirement, Set.of(), host ? Set.of("test") : Set.of(), CredentialForwarding.stripAll(), EndpointStatus.ENABLED);
            IngressAccessSnapshot snapshot = new IngressAccessSnapshot(1, new IngressAccessContent(new EndpointRegistry(List.of(endpoint)),
                    Map.of(), Map.of(), Map.of(endpoint.endpointId(), policy)), "hash", Instant.EPOCH);
            attributes.put(GatewayRequestAttributes.SNAPSHOT, snapshot);
            attributes.put(GatewayRequestAttributes.ENDPOINT, endpoint);
        }
        return (ServerWebExchange) Proxy.newProxyInstance(ServerWebExchange.class.getClassLoader(),
                new Class<?>[]{ServerWebExchange.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getAttribute" -> attributes.get(args[0]);
                    case "getAttributes" -> attributes;
                    case "getRequest" -> request;
                    default -> throw new AssertionError("Unexpected exchange method " + method.getName());
                });
    }
}
