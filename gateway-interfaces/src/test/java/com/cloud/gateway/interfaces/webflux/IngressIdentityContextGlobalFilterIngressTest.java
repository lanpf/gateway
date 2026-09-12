package com.cloud.gateway.interfaces.webflux;

import static org.junit.jupiter.api.Assertions.*;

import com.cloud.framework.core.RequestHeader;
import com.cloud.gateway.application.ingress.IngressAccessCommandService;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientPolicy;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.application.policy.PolicySnapshotProvider;
import com.cloud.gateway.application.policy.VersionedPolicySnapshot;
import com.cloud.gateway.application.security.ClientCredentialAuthenticator;
import com.cloud.gateway.application.security.ClientSignatureEvidence;
import com.cloud.gateway.domain.access.*;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.access.client.VerifiedClient;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.SubjectAuthencationRequirement;
import com.cloud.gateway.domain.policy.bundle.*;
import com.cloud.gateway.domain.policy.capability.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

class IngressIdentityContextGlobalFilterIngressTest {
    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final AppId APP = new AppId("app");
    private static final ClientId CLIENT = new ClientId("client");
    private static final CredentialId CREDENTIAL = new CredentialId("client");
    private static final Endpoint ENDPOINT = new Endpoint(new EndpointId("CHECK"), "gateway",
            List.of(new EndpointMatcher("POST", "/admin/gateway/**")), GatewayRequestAttributes.LOCAL_ROUTE_ID);

    @Test
    void shouldRejectRequestsThatBypassSnapshotPreparation() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/admin/gateway/test"));
        filter((credentials, evidence, now) -> {
            throw new AssertionError("unprepared request must not authenticate");
        }, allowedQuotas()).filter(exchange, outgoing -> {
            throw new AssertionError("unprepared request must not forward");
        }).block(WAIT);
        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldRejectSnapshotAndEndpointWithoutCompletedPreparation() {
        MockServerWebExchange exchange = exchange();
        exchange.getAttributes().remove(GatewayRequestAttributes.PREPARATION_COMPLETE);
        filter((credentials, evidence, now) -> {
            throw new AssertionError("incomplete preparation must not authenticate");
        }, allowedQuotas()).filter(exchange, outgoing -> {
            throw new AssertionError("incomplete preparation must not forward");
        }).block(WAIT);
        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldAuthenticateLazilyUsingOneSnapshotAndUnmodifiedEvidenceBeforeForwarding() {
        IngressAccessSnapshot initial = snapshot(1, true);
        AtomicReference<IngressAccessSnapshot> current = new AtomicReference<>(initial);
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger authentications = new AtomicInteger();
        AtomicReference<ClientSignatureEvidence> captured = new AtomicReference<>();
        PolicySnapshotProvider provider = () -> {
            reads.incrementAndGet();
            return new VersionedPolicySnapshot(current.get(), "revision");
        };
        TrustedRequestContextGlobalFilter filter = filter((credentials, evidence, now) -> {
            authentications.incrementAndGet();
            captured.set(evidence);
            assertSame(initial.content().clients().get(CLIENT).credentials(), credentials);
            return CompletableFuture.completedFuture(verified());
        }, allowedQuotas());
        MockServerWebExchange exchange = exchange();
        exchange.getAttributes().put(GatewayRequestAttributes.SNAPSHOT, provider.current().snapshot());
        AtomicInteger forwarded = new AtomicInteger();
        Mono<Void> request = filter.filter(exchange, outgoing -> {
            forwarded.incrementAndGet();
            assertEquals(1, authentications.get());
            assertSame(initial, outgoing.getAttribute(GatewayRequestAttributes.SNAPSHOT));
            assertEquals("client", outgoing.getRequest().getHeaders().getFirst(RequestHeader.CLIENT_ID.headerName()));
            assertNull(outgoing.getRequest().getHeaders().getFirst(RequestHeader.SIGNATURE.headerName()));
            assertNull(outgoing.getRequest().getHeaders().getFirst("X-Gateway-Forged"));
            return DataBufferUtils.join(outgoing.getRequest().getBody()).doOnNext(buffer -> {
                byte[] body = new byte[buffer.readableByteCount()];
                buffer.read(body);
                DataBufferUtils.release(buffer);
                assertEquals("original-body", new String(body, StandardCharsets.UTF_8));
            }).then();
        });
        assertEquals(0, authentications.get());
        assertEquals(0, forwarded.get());
        current.set(snapshot(2, false));
        request.block(WAIT);
        assertEquals(1, reads.get());
        assertEquals(1, authentications.get());
        assertEquals(1, forwarded.get());
        assertEquals("/admin/gateway/a%2Fb", captured.get().rawPath());
        assertEquals("q=1&q=%2F", captured.get().rawQuery());
        assertEquals("original-body", new String(captured.get().payload(), StandardCharsets.UTF_8));
        assertEquals("signature", captured.get().signature());
        assertEquals("nonce", captured.get().nonce());
        assertEquals("client", captured.get().credentialId());
    }

    @Test
    void shouldCancelPendingAuthenticationWithoutForwarding() {
        CompletableFuture<ClientVerificationOutcome> pending = new CompletableFuture<>();
        TrustedRequestContextGlobalFilter filter = filter((credentials, evidence, now) -> pending,
                allowedQuotas());
        Disposable subscription = filter.filter(exchange(), outgoing -> {
            fail("pending authentication must not forward");
            return Mono.empty();
        }).subscribe();
        assertFalse(pending.isDone());
        subscription.dispose();
        assertTrue(pending.isCancelled());
    }

    @Test
    void shouldRejectAuthenticationBeforeQuotaOrHeaderRewrite() {
        AtomicInteger quotas = new AtomicInteger();
        TrustedRequestContextGlobalFilter filter = filter((credentials, evidence, now) ->
                CompletableFuture.completedFuture(ClientVerificationOutcome.rejected()),
                QuotaTestSupport.guard((rule, value) -> {
                    quotas.incrementAndGet();
                    return RateLimitDecision.allowed(rule.dimension(), value, 1);
                }));
        MockServerWebExchange exchange = exchange();
        filter.filter(exchange, outgoing -> {
            fail("rejected authentication must not forward");
            return Mono.empty();
        }).block(WAIT);
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals(0, quotas.get());
        assertNull(exchange.getAttribute(GatewayRequestAttributes.TRUSTED_CONTEXT));
        assertEquals("signature", exchange.getRequest().getHeaders().getFirst(RequestHeader.SIGNATURE.headerName()));
    }

    @Test
    void shouldSkipPreviouslyAppliedIpQuotaAndStopAtPostAuthenticationQuota() {
        AtomicInteger calls = new AtomicInteger();
        TrustedRequestContextGlobalFilter filter = filter((credentials, evidence, now) ->
                CompletableFuture.completedFuture(verified()), QuotaTestSupport.guard((rule, value) -> {
                    calls.incrementAndGet();
                    assertEquals(RateLimitDimension.CLIENT, rule.dimension());
                    assertEquals("client", value);
                    return RateLimitDecision.rejected(rule.dimension(), value, Duration.ofSeconds(1));
                }));
        MockServerWebExchange exchange = exchange();
        exchange.getAttributes().put(GatewayRequestAttributes.APPLIED_IP_RULES, new java.util.HashSet<>(Set.of("ip")));
        filter.filter(exchange, outgoing -> {
            fail("quota rejection must not forward");
            return Mono.empty();
        }).block(WAIT);
        assertEquals(1, calls.get());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertNotNull(exchange.getAttribute(GatewayRequestAttributes.TRUSTED_CONTEXT));
        assertEquals("signature", exchange.getRequest().getHeaders().getFirst(RequestHeader.SIGNATURE.headerName()));
    }

    private static TrustedRequestContextGlobalFilter filter(
            ClientCredentialAuthenticator authenticator, RequestQuotaGuard quotas) {
        return new TrustedRequestContextGlobalFilter(
                new IngressAccessCommandService(authenticator, new IngressAccessEvaluator(),
                        new PolicyResolver(), Clock.fixed(NOW, ZoneOffset.UTC)),
                authentication -> Mono.error(new AssertionError("host must not be invoked")),
                cookie -> Mono.error(new AssertionError("browser must not be invoked")), quotas, null);
    }

    private static MockServerWebExchange exchange() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.POST,
                        URI.create("http://localhost/admin/gateway/a%2Fb?q=1&q=%2F"))
                .header(RequestHeader.APP_ID.headerName(), "app")
                .header(RequestHeader.CLIENT_ID.headerName(), "client")
                .header(RequestHeader.TIMESTAMP.headerName(), "123")
                .header(RequestHeader.SIGNATURE.headerName(), "signature")
                .header(RequestHeader.CREDENTIAL_ID.headerName(), "client")
                .header(RequestHeader.NONCE.headerName(), "nonce")
                .header("X-Gateway-Forged", "external")
                .body("original-body"));
        exchange.getAttributes().put(GatewayRequestAttributes.SNAPSHOT, snapshot(1, true));
        exchange.getAttributes().put(GatewayRequestAttributes.ENDPOINT, ENDPOINT);
        exchange.getAttributes().put(GatewayRequestAttributes.PREPARATION_COMPLETE, true);
        exchange.getAttributes().put(GatewayRequestAttributes.APPLIED_IP_RULES, new java.util.HashSet<String>());
        return exchange;
    }

    private static RequestQuotaGuard allowedQuotas() {
        return QuotaTestSupport.guard((rule, value) -> RateLimitDecision.allowed(rule.dimension(), value, 1));
    }

    private static PolicySnapshotProvider provider() {
        return () -> new VersionedPolicySnapshot(snapshot(1, true), "revision");
    }

    private static ClientVerificationOutcome verified() {
        return ClientVerificationOutcome.verified(new VerifiedClient(
                ClientVerificationMode.REQUEST_SIGNATURE, CREDENTIAL, CLIENT, NOW));
    }

    private static IngressAccessSnapshot snapshot(long version, boolean enabled) {
        Credentials credentials = new Credentials(new Credential(CREDENTIAL, CLIENT,
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1, "secret-ref", NOW.minusSeconds(60),
                NOW.plusSeconds(3600)), null);
        EndpointPolicy endpoint = new EndpointPolicy(ENDPOINT.endpointId(), ClientVerificationRequirement.REQUIRED,
                ClientVerificationMode.REQUEST_SIGNATURE, SubjectAuthencationRequirement.IGNORE, Set.of(), Set.of(),
                CredentialForwarding.stripAll(), EndpointStatus.ENABLED);
        PolicyBundleId id = new PolicyBundleId("quotas");
        RateLimitRules limits = new RateLimitRules(List.of(
                new RateLimitPolicy("ip", RateLimitDimension.IP, 10, 10, RateLimitMode.EXACT),
                new RateLimitPolicy("client", RateLimitDimension.CLIENT, 10, 10, RateLimitMode.EXACT)), RedisFailurePolicy.CLOSED);
        PolicyComposition policies = new PolicyComposition(Map.of(id, new PolicyBundle(id, limits, null, null, null, null)),
                List.of(new PolicyBinding(new PolicyBindingId("quotas"), id, null, null, Set.of(), null, null, null)));
        return new IngressAccessSnapshot(version, new IngressAccessContent(new EndpointRegistry(List.of(ENDPOINT)),
                Map.of(APP, new AppPolicy(APP, "web", Set.of(), enabled ? PolicyStatus.ENABLED : PolicyStatus.DISABLED)),
                Map.of(CLIENT, new ClientPolicy(CLIENT, Set.of(APP), credentials, ClientStatus.ENABLED)),
                Map.of(ENDPOINT.endpointId(), endpoint), policies), "hash-" + version, NOW);
    }
}
