package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.domain.capability.configuration.EffectiveCapabilityConfig;

import com.cloud.gateway.application.ingress.CapabilityRegistration;

import com.cloud.gateway.application.configuration.publication.RevisionedAccessSnapshot;
import com.cloud.gateway.application.ingress.*;
import com.cloud.gateway.application.quota.QuotaCommandService;
import com.cloud.gateway.domain.app.*;
import com.cloud.gateway.domain.capability.configuration.CapabilityBinding;
import com.cloud.gateway.domain.capability.configuration.CapabilityBindingId;
import com.cloud.gateway.domain.capability.configuration.CapabilityBundle;
import com.cloud.gateway.domain.capability.configuration.CapabilityBundleId;
import com.cloud.gateway.domain.capability.configuration.CapabilityComposition;
import com.cloud.gateway.domain.capability.configuration.CapabilityResolver;
import com.cloud.gateway.domain.capability.runtime.CapabilityExecution;
import com.cloud.gateway.domain.capability.runtime.HeaderApplyCapability;
import com.cloud.gateway.domain.capability.runtime.RateLimitCapability;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.runtime.TimeoutCapability;
import com.cloud.gateway.domain.capability.resilience.TimeoutPolicy;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.endpoint.*;
import com.cloud.gateway.domain.ingress.access.AccessEvaluator;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import com.cloud.gateway.domain.ingress.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.ingress.snapshot.*;
import com.cloud.framework.core.RequestHeader;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.RouteMetadataUtils;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

class IngressFiltersTest {
    private static final AppId APP = new AppId("app");
    private static final String PATH = "/admin/gateway/test";
    private static final CapabilityBundleId TIMEOUT_BUNDLE = new CapabilityBundleId("public-read");
    private static final CapabilityBindingId TIMEOUT_BINDING = new CapabilityBindingId("all-ingress");
    /** Long enough not to race the assertion, short enough to keep the suite fast. */
    private static final TimeoutPolicy TIMEOUT = new TimeoutPolicy(
            Duration.ofMillis(20), Duration.ofMillis(30), Duration.ofMillis(50));

    @Test
    void shouldReusePreparedSnapshotWithoutRepeatingPreparation() {
        AtomicInteger captures = new AtomicInteger();
        AtomicInteger quotas = new AtomicInteger();
        AtomicInteger forwarded = new AtomicInteger();
        IngressSnapshot snapshot = snapshot(endpoint(GatewayRequestAttributes.LOCAL_ROUTE_ID));
        PreparationStageCommandService preparation = new PreparationStageCommandService(() -> {
            captures.incrementAndGet();
            return new RevisionedAccessSnapshot(snapshot, new Revision("source"));
        }, new SnapshotEndpointMatchSource(), new CapabilityExecutionCommandService(new CapabilityResolver(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry()),
                planner(), List.of(new RateLimitCapabilityExecutor(planned -> {
            quotas.incrementAndGet();
            return CompletableFuture.completedFuture(RateLimitDecision.allowed(null, null, 1));
        }), new TimeoutCapabilityExecutor(), new HeaderApplyCapabilityExecutor())), () -> { });
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
        MockServerWebExchange exchange = preparedExchange(endpoint, snapshot(endpoint));
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

    /**
     * The baseline the timeout test is measured against: with no bundle bound, the FORWARD phase plans
     * no unit and the upstream call is issued without a budget. Without this case a green timeout test
     * could not tell "the budget was enforced" from "every request is rejected".
     */
    @Test
    void shouldForwardWithoutABudgetWhenNoTimeoutIsBound() {
        Endpoint endpoint = anonymousEndpoint(GatewayRequestAttributes.LOCAL_ROUTE_ID);
        MockServerWebExchange exchange = preparedExchange(endpoint, snapshot(endpoint));
        AtomicInteger forwarded = new AtomicInteger();
        admissionFilter().filter(exchange, ignored -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        }).block();
        assertEquals(1, forwarded.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    /**
     * The header plan is now applied from the FORWARD phase's outcome rather than before it, so this
     * pins the security-relevant result of that move: a forged identity header the caller sent must be
     * gone by the time the request reaches the downstream, replaced by the verified value.
     */
    @Test
    void shouldApplyTheHeaderPlanBeforeTheCallIsIssued() {
        Endpoint endpoint = anonymousEndpoint(GatewayRequestAttributes.LOCAL_ROUTE_ID);
        MockServerWebExchange exchange = preparedExchange(endpoint, snapshot(endpoint),
                MockServerHttpRequest.get(PATH).header(RequestHeader.APP_ID.headerName(), "forged"));
        List<String> delivered = new ArrayList<>();
        admissionFilter().filter(exchange, forwarded -> {
            delivered.add(forwarded.getRequest().getHeaders().getFirst(RequestHeader.APP_ID.headerName()));
            forwarded.getResponse().setStatusCode(HttpStatus.OK);
            return forwarded.getResponse().setComplete();
        }).block();

        assertEquals(List.of(APP.value()), delivered, "the caller's own value must not be forwarded");
    }

    @Test
    void shouldAnswerGatewayTimeoutWhenTheForwardBudgetExpires() {
        Endpoint endpoint = anonymousEndpoint(GatewayRequestAttributes.LOCAL_ROUTE_ID);
        MockServerWebExchange exchange = preparedExchange(endpoint, snapshot(endpoint, timeoutComposition()));
        admissionFilter().filter(exchange, ignored -> Mono.never()).block();
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldAnswerBadGatewayOnConnectionFailure() {
        Endpoint endpoint = anonymousEndpoint(GatewayRequestAttributes.LOCAL_ROUTE_ID);
        MockServerWebExchange exchange = preparedExchange(endpoint, snapshot(endpoint));
        admissionFilter().filter(exchange, ignored -> Mono.error(new java.io.IOException("connection failed"))).block();
        assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldPreserveTransportResponseTimeoutProjection() {
        Endpoint endpoint = anonymousEndpoint(GatewayRequestAttributes.LOCAL_ROUTE_ID);
        MockServerWebExchange exchange = preparedExchange(endpoint, snapshot(endpoint));
        admissionFilter().filter(exchange, ignored -> Mono.error(
                new org.springframework.web.server.ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT))).block();
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldPropagateUnclassifiedFailureWithoutReplacingResponse() {
        Endpoint endpoint = anonymousEndpoint(GatewayRequestAttributes.LOCAL_ROUTE_ID);
        MockServerWebExchange exchange = preparedExchange(endpoint, snapshot(endpoint));
        IllegalStateException failure = new IllegalStateException("unexpected failure");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> admissionFilter().filter(exchange, ignored -> Mono.error(failure)).block()));
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldNotReplaceCommittedResponseOnTimeout() {
        Endpoint endpoint = anonymousEndpoint(GatewayRequestAttributes.LOCAL_ROUTE_ID);
        MockServerWebExchange exchange = preparedExchange(endpoint, snapshot(endpoint));
        org.springframework.web.server.ResponseStatusException failure =
                new org.springframework.web.server.ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT);
        assertSame(failure, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> admissionFilter().filter(exchange, forwarded -> {
                    forwarded.getResponse().setStatusCode(HttpStatus.OK);
                    return forwarded.getResponse().setComplete().then(Mono.error(failure));
                }).block()));
        assertTrue(exchange.getResponse().isCommitted());
        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
    }

    /**
     * The declared budget has to reach the transport, and the only channel for that is the route
     * metadata the Spring Cloud Gateway client reads -- so this pins the filter actually publishing it
     * rather than merely planning it.
     */
    @Test
    void shouldPublishTheDeclaredBudgetToTheRouteMetadata() {
        Endpoint endpoint = anonymousEndpoint(GatewayRequestAttributes.LOCAL_ROUTE_ID);
        MockServerWebExchange exchange = preparedExchange(endpoint, snapshot(endpoint, timeoutComposition()));
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, Route.async()
                .id("orders").uri(URI.create("http://localhost:9000")).order(0)
                .asyncPredicate(ignored -> Mono.just(true)).build());

        admissionFilter().filter(exchange, ignored -> Mono.never()).block();

        Map<String, Object> metadata = exchange
                .<Route>getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR).getMetadata();
        assertEquals((int) TIMEOUT.connect().toMillis(), metadata.get(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR));
        assertEquals(TIMEOUT.read().toMillis(), metadata.get(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR));
    }

    private static IngressAdmissionGlobalFilter admissionFilter() {
        RequestQuotaGuard quotas = quotaGuard();
        AccessStageCommandService access = new AccessStageCommandService((credentials, client, evidence, now) -> {
            throw new AssertionError("no client verification expected");
        }, new AccessEvaluator(), Clock.systemUTC());
        CapabilityExecutionCommandService capabilities = new CapabilityExecutionCommandService(
                new CapabilityResolver(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry()), planner(), List.of(new RateLimitCapabilityExecutor(quotas),
                        new TimeoutCapabilityExecutor(), new HeaderApplyCapabilityExecutor()));
        return new IngressAdmissionGlobalFilter(new IngressAdmissionCommandService(access,
                new AdmissionStageCommandService(capabilities)),
                authentication -> Mono.just(SubjectAuthenticationOutcome.absent()),
                cookie -> Mono.just(SubjectAuthenticationOutcome.absent()), responses(quotas), capabilities);
    }

    /**
     * Mirrors runtime registration. A capability that is planned but has no executor fails only when a
     * request reaches it, so the two lists must be kept in step here as well as in the configuration.
     */
    private static List<CapabilityRegistration<?>> planner() {
        return List.of(new CapabilityRegistration<>(new RateLimitCapability(), config -> config.get(com.cloud.gateway.domain.capability.ratelimit.RateLimitConfigDefinition.KEY)), new CapabilityRegistration<>(new TimeoutCapability(), config -> config.get(com.cloud.gateway.domain.capability.resilience.TimeoutConfigDefinition.KEY)),
                new CapabilityRegistration<>(new HeaderApplyCapability(), config -> config.get(com.cloud.gateway.domain.capability.header.HeaderConfigDefinition.KEY)));
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

    /**
     * The prepared-exchange fixture: the preparation stage's output is written by hand. Going through
     * {@code IngressPreparationWebFilter} would re-run endpoint matching, and the admission stage also
     * requires the completion marker, so a fixture is the only way to start a test inside the pipeline.
     * Both keys are mandatory — the admission filter rejects when either is absent.
     */
    private static MockServerWebExchange preparedExchange(Endpoint endpoint, IngressSnapshot snapshot) {
        return preparedExchange(endpoint, snapshot, MockServerHttpRequest.get(PATH));
    }

    private static MockServerWebExchange preparedExchange(Endpoint endpoint, IngressSnapshot snapshot,
            MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(GatewayRequestAttributes.ENDPOINT, endpoint);
        exchange.getAttributes().put(GatewayRequestAttributes.PREPARATION_COMPLETE, Boolean.TRUE);
        exchange.getAttributes().put(GatewayRequestAttributes.PREPARED, new PreparationStageOutput.Prepared(
                snapshot, EndpointMatch.of(List.of(endpoint)),
                new RequestEvidence("GET", PATH, null, APP, null, null, "127.0.0.1"),
                CapabilityExecution.empty()));
        return exchange;
    }

    /** A required subject cannot pass with the absent authenticator these tests install. */
    private static Endpoint endpoint(String route) {
        return endpoint(route, SubjectAuthenticationRequirement.REQUIRED);
    }

    private static Endpoint anonymousEndpoint(String route) {
        return endpoint(route, SubjectAuthenticationRequirement.ANONYMOUS);
    }

    private static Endpoint endpoint(String route, SubjectAuthenticationRequirement subject) {
        return new Endpoint(new EndpointId("ADMIN.TEST"), new ServiceId("gateway"), new RouteId(route),
                List.of(new EndpointMatcher("GET", PATH)), Set.of(), EndpointStatus.ENABLED,
                new EndpointAccess(ClientVerificationRequirement.OPTIONAL, subject, Set.of(), Set.of()));
    }

    private static IngressSnapshot snapshot(Endpoint endpoint) {
        return snapshot(endpoint, CapabilityComposition.empty());
    }

    private static IngressSnapshot snapshot(Endpoint endpoint, CapabilityComposition capabilities) {
        return new IngressSnapshot(new SnapshotVersion(1), new IngressContent(
                new AppRegistry(List.of(new App(APP, "web", AppStatus.ENABLED, new AppAccess(Set.of())))),
                new ClientRegistry(List.of()), new EndpointRegistry(List.of(endpoint)), capabilities),
                "hash", Instant.parse("2026-09-19T00:00:00Z"));
    }

    /**
     * A timeout bundle behind a globally scoped binding, so {@code CapabilityResolver} matches it for
     * any endpoint. The bundle id and the binding id are deliberately different values: they are two
     * identities, and the composition rejects a bundle whose map key disagrees with its own id.
     */
    private static CapabilityComposition timeoutComposition() {
        return new CapabilityComposition(
                Map.of(TIMEOUT_BUNDLE, new CapabilityBundle(TIMEOUT_BUNDLE, java.util.List.of(TIMEOUT))),
                List.of(new CapabilityBinding(TIMEOUT_BINDING, TIMEOUT_BUNDLE, null, null, Set.of(),
                        null, null, null)));
    }
}
