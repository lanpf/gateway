package com.cloud.gateway.interfaces.webflux;

import com.cloud.framework.core.RequestHeader;

import static org.junit.jupiter.api.Assertions.*;

import com.cloud.gateway.application.policy.PolicySnapshotProvider;
import com.cloud.gateway.application.policy.VersionedPolicySnapshot;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.SubjectAuthencationRequirement;
import com.cloud.gateway.domain.policy.bundle.*;
import com.cloud.gateway.domain.policy.capability.*;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.policy.CredentialForwarding;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class IngressAccessSnapshotWebFilterTest {
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final Endpoint ENDPOINT = new Endpoint(new EndpointId("TEST.GET"), "service",
            List.of(new EndpointMatcher("GET", "/known")), "route");

    @Test
    void shouldPinSnapshotAndEndpointBeforeSecurityAndRetainThemWhenProviderChanges() {
        IngressAccessSnapshot initial = snapshot(1);
        AtomicReference<IngressAccessSnapshot> latest = new AtomicReference<>(initial);
        var reads = new AtomicInteger();
        PolicySnapshotProvider provider = () -> {
            reads.incrementAndGet();
            return new VersionedPolicySnapshot(latest.get(), "revision");
        };
        var filter = new PolicySnapshotWebFilter(provider, allowedQuotas());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/known"));
        var calls = new AtomicInteger();
        filter.filter(exchange, captured -> {
            assertSame(initial, captured.getAttribute(GatewayRequestAttributes.SNAPSHOT));
            assertSame(ENDPOINT, captured.getAttribute(GatewayRequestAttributes.ENDPOINT));
            latest.set(snapshot(2));
            return Mono.fromRunnable(() -> {
                assertSame(initial, captured.getAttribute(GatewayRequestAttributes.SNAPSHOT));
                calls.incrementAndGet();
            });
        }).block(WAIT);
        assertEquals(1, reads.get());
        assertEquals(1, calls.get());
        assertTrue(filter.getOrder() < -100, "must precede Spring Security WebFilterChainProxy");
    }

    @Test
    void shouldRejectDuplicateIdentityAndSignatureHeadersBeforeCallingSecurity() {
        for (String header : List.of(RequestHeader.APP_ID.headerName(), RequestHeader.CLIENT_ID.headerName(), RequestHeader.SIGNATURE.headerName(),
                RequestHeader.TIMESTAMP.headerName(), RequestHeader.CREDENTIAL_ID.headerName(), RequestHeader.NONCE.headerName())) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/known")
                    .header(header, "one").header(header.toLowerCase(java.util.Locale.ROOT), "two"));
            filter(allowedQuotas()).filter(exchange, ignored -> {
                fail("duplicate " + header + " reached security");
                return Mono.empty();
            }).block(WAIT);
            assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode(), header);
        }
    }

    @Test
    void shouldRejectOversizedIdentityHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/known").header(RequestHeader.APP_ID.headerName(), "x".repeat(4097)));
        filter(allowedQuotas()).filter(exchange, ignored -> {
            fail("oversized identity reached security");
            return Mono.empty();
        }).block(WAIT);
        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldApplyGlobalIpQuotaToUnknownPathBeforeReturningNotFound() {
        var calls = new AtomicInteger();
        RequestQuotaGuard quotas = QuotaTestSupport.guard((rule, key) -> {
            calls.incrementAndGet();
            assertEquals("ip-global", rule.ruleId());
            assertEquals("192.0.2.10", key);
            return RateLimitDecision.allowed(rule.dimension(), key, 9);
        });
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/unknown")
                .remoteAddress(new InetSocketAddress("192.0.2.10", 12345))
                .header("X-Forwarded-For", "198.51.100.99"));
        filter(quotas).filter(exchange, ignored -> {
            fail("unknown endpoint reached security");
            return Mono.empty();
        }).block(WAIT);
        assertEquals(1, calls.get());
        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertEquals(Set.of("ip-global"), exchange.getAttribute(GatewayRequestAttributes.APPLIED_IP_RULES));
        assertNull(exchange.getAttribute(GatewayRequestAttributes.ENDPOINT));
        assertNotNull(exchange.getAttribute(GatewayRequestAttributes.SNAPSHOT));
    }

    @Test
    void shouldReturnQuotaRejectionInsteadOfNotFoundForUnknownPath() {
        RequestQuotaGuard quotas = QuotaTestSupport.guard((rule, key) -> RateLimitDecision.rejected(rule.dimension(), key, Duration.ofMillis(1500)));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/unknown"));
        filter(quotas).filter(exchange, ignored -> Mono.error(new AssertionError("must not continue"))).block(WAIT);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("2", exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void shouldReturnServiceUnavailableWhenIpQuotaFailsClosed() {
        RequestQuotaGuard quotas = QuotaTestSupport.guard((rule, key) -> RateLimitDecision.unavailable(rule.dimension(), key));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/unknown"));
        filter(quotas).filter(exchange, ignored -> Mono.error(new AssertionError("must not continue"))).block(WAIT);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertFalse(exchange.getResponse().getHeaders().containsKey(HttpHeaders.RETRY_AFTER));
    }

    private static PolicySnapshotWebFilter filter(RequestQuotaGuard quotas) {
        IngressAccessSnapshot snapshot = snapshot(1);
        return new PolicySnapshotWebFilter(() -> new VersionedPolicySnapshot(snapshot, "revision"), quotas);
    }

    private static RequestQuotaGuard allowedQuotas() {
        return QuotaTestSupport.guard((rule, key) -> RateLimitDecision.allowed(rule.dimension(), key, 9));
    }

    private static IngressAccessSnapshot snapshot(long version) {
        var id = new PolicyBundleId("global-protection");
        var rules = new RateLimitRules(List.of(
                new RateLimitPolicy("ip-global", RateLimitDimension.IP, 10, 10, RateLimitMode.EXACT),
                new RateLimitPolicy("app-global", RateLimitDimension.APP, 10, 10, RateLimitMode.EXACT)), RedisFailurePolicy.CLOSED);
        var bundle = new PolicyBundle(id, rules, null, null, null, null);
        var binding = new PolicyBinding(new PolicyBindingId("global"), id, null, null, Set.of(), null, null, null);
        var policies = new PolicyComposition(Map.of(id, bundle), List.of(binding));
        var access = new EndpointPolicy(ENDPOINT.endpointId(), ClientVerificationRequirement.OPTIONAL, ClientVerificationMode.IGNORE, SubjectAuthencationRequirement.IGNORE, Set.of(), Set.of(), CredentialForwarding.stripAll(), EndpointStatus.ENABLED);
        return new IngressAccessSnapshot(version, new IngressAccessContent(new EndpointRegistry(List.of(ENDPOINT)),
                Map.of(), Map.of(), Map.of(ENDPOINT.endpointId(), access), policies), "hash-" + version, Instant.EPOCH);
    }
}
