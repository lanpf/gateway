package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.application.configuration.publication.RevisionedAccessSnapshot;
import com.cloud.gateway.application.ingress.*;
import com.cloud.gateway.application.quota.QuotaCommandService;
import com.cloud.gateway.domain.app.*;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.endpoint.*;
import com.cloud.gateway.domain.ingress.access.AccessEvaluator;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import com.cloud.gateway.domain.ingress.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.ingress.snapshot.*;
import com.cloud.gateway.domain.policy.bundle.CapabilityComposition;
import com.cloud.gateway.domain.policy.bundle.CapabilityResolver;
import com.cloud.gateway.domain.policy.execution.*;
import com.cloud.gateway.domain.policy.capability.RateLimitCapability;
import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

class IngressFiltersTest {
    private static final AppId APP = new AppId("app");
    private static final String PATH = "/admin/gateway/test";

    @Test
    void shouldReusePreparedSnapshotWithoutRepeatingPreparation() {
        AtomicInteger captures = new AtomicInteger();
        AtomicInteger quotas = new AtomicInteger();
        AtomicInteger forwarded = new AtomicInteger();
        IngressSnapshot snapshot = snapshot(endpoint(GatewayRequestAttributes.LOCAL_ROUTE_ID));
        PreparationStageCommandService preparation = new PreparationStageCommandService(() -> {
            captures.incrementAndGet();
            return new RevisionedAccessSnapshot(snapshot, new Revision("source"));
        }, new SnapshotEndpointMatchSource(), new CapabilityExecutionCommandService(new CapabilityResolver(),
                new CapabilityPlanner(List.of(new RateLimitCapability())), List.of(new RateLimitCapabilityExecutor(planned -> {
            quotas.incrementAndGet();
            return CompletableFuture.completedFuture(RateLimitDecision.allowed(null, null, 1));
        }))), () -> { });
        IngressPreparationWebFilter filter = new IngressPreparationWebFilter(preparation, responses(quotaGuard()));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(PATH));
        filter.filter(exchange, first -> filter.filter(first, second -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        })).block();
        PreparationStageOutput.Prepared prepared = exchange.getAttribute(GatewayRequestAttributes.PREPARED);
        assertNotNull(prepared);
        assertSame(snapshot, prepared.snapshot());
        assertEquals(1, captures.get());
        assertEquals(0, quotas.get());
        assertEquals(1, forwarded.get());
    }

    @Test
    void shouldRouteLocalEndpointIntoAdmissionAndRejectMissingSubject() {
        Endpoint endpoint = endpoint(GatewayRequestAttributes.LOCAL_ROUTE_ID);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(PATH));
        exchange.getAttributes().put(GatewayRequestAttributes.ENDPOINT, endpoint);
        exchange.getAttributes().put(GatewayRequestAttributes.PREPARATION_COMPLETE, true);
        exchange.getAttributes().put(GatewayRequestAttributes.PREPARED, new PreparationStageOutput.Prepared(
                snapshot(endpoint), EndpointMatch.of(List.of(endpoint)),
                new RequestEvidence("GET", PATH, null, APP, null, null, "127.0.0.1"),
                CapabilityExecution.empty()));
        new LocalPolicyWebFilter(admissionFilter()).filter(exchange, ignored -> {
            throw new AssertionError("missing required subject must not reach controller");
        }).block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldLeaveProxyEndpointForTheGatewayFilterChain() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(PATH));
        exchange.getAttributes().put(GatewayRequestAttributes.ENDPOINT, endpoint("proxy"));
        AtomicInteger forwarded = new AtomicInteger();
        new LocalPolicyWebFilter(admissionFilter()).filter(exchange, ignored -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        }).block();
        assertEquals(1, forwarded.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldRejectGatewayRequestsThatBypassPreparation() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(PATH));
        admissionFilter().filter(exchange, ignored -> {
            throw new AssertionError("unprepared request must not be forwarded");
        }).block();
        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
    }

    private static IngressAdmissionGlobalFilter admissionFilter() {
        RequestQuotaGuard quotas = quotaGuard();
        AccessStageCommandService access = new AccessStageCommandService((credentials, client, evidence, now) -> {
            throw new AssertionError("no client verification expected");
        }, new AccessEvaluator(), Clock.systemUTC());
        return new IngressAdmissionGlobalFilter(new IngressAdmissionCommandService(access,
                new AdmissionStageCommandService(new CapabilityExecutionCommandService(new CapabilityResolver(),
                        new CapabilityPlanner(List.of(new RateLimitCapability())), List.of(new RateLimitCapabilityExecutor(quotas))))),
                authentication -> Mono.just(SubjectAuthenticationOutcome.absent()),
                cookie -> Mono.just(SubjectAuthenticationOutcome.absent()), responses(quotas));
    }

    private static CapabilityResponseWriter responses(RequestQuotaGuard quotas) {
        return new CapabilityResponseWriter(List.of(new RateLimitOutcomeWriter(quotas)));
    }

    private static RequestQuotaGuard quotaGuard() {
        return new RequestQuotaGuard(new QuotaCommandService((rule, key) -> {
            throw new AssertionError("no quota rules configured");
        }, (rule, key) -> {
            throw new AssertionError("no fallback expected");
        }, (rule, decision) -> { }));
    }

    private static Endpoint endpoint(String route) {
        return new Endpoint(new EndpointId("ADMIN.TEST"), new ServiceId("gateway"), new RouteId(route),
                List.of(new EndpointMatcher("GET", PATH)), Set.of(), EndpointStatus.ENABLED,
                new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                        SubjectAuthenticationRequirement.REQUIRED, Set.of(), Set.of()));
    }

    private static IngressSnapshot snapshot(Endpoint endpoint) {
        return new IngressSnapshot(new SnapshotVersion(1), new IngressContent(
                new AppRegistry(List.of(new App(APP, "web", AppStatus.ENABLED, new AppAccess(Set.of())))),
                new ClientRegistry(List.of()), new EndpointRegistry(List.of(endpoint)), CapabilityComposition.empty()),
                "hash", Instant.parse("2026-09-19T00:00:00Z"));
    }
}
