package com.cloud.gateway.ingress;

import com.cloud.gateway.domain.capability.policy.CapabilityBinding;
import com.cloud.gateway.domain.capability.policy.CapabilityBundle;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
import com.cloud.gateway.domain.capability.policy.CapabilityBindingId;
import com.cloud.gateway.domain.capability.policy.CapabilityBundleId;

import com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer;
import com.cloud.gateway.application.configuration.publication.RevisionedAccessSnapshot;
import com.cloud.gateway.application.quota.QuotaCommandService;
import com.cloud.gateway.application.quota.QuotaObserver;
import com.cloud.gateway.application.security.BrowserSessionVerifier;
import com.cloud.gateway.application.security.BrowserSessionVerificationOutcome;
import com.cloud.gateway.application.security.ClientCredentialAuthenticator;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.application.quota.QuotaAcquirer;
import com.cloud.gateway.domain.capability.header.HeaderConfig;
import com.cloud.gateway.domain.capability.resilience.TimeoutConfig;
import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.ingress.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.app.AppAccess;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.capability.header.CredentialForwarding;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.interfaces.config.IngressWebFluxConfiguration;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.reactor.ReactorAutoConfiguration;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Isolated HTTP smoke for the v1 ingress chain: production prepare/access/admit filters plus the
 * security-required header apply, no external user-auth, Nacos or stage-two downstream governance.
 * Stage two (cache, circuit breaker, timeout) is not part of the v1 request path, so these tests
 * assert only that an admitted request is forwarded with the header plan applied.
 */
class GatewayPipelineIT {
    private static final AppId APP = new AppId("public-app");
    private static final String ROUTE = "pipeline-route";
    private static final String SERVICE = "pipeline-downstream";
    private static final String OBSERVE_SECONDS = "gateway.it.manual-observation-seconds";
    private final AtomicReference<RevisionedAccessSnapshot> snapshots = new AtomicReference<>();
    private final AtomicInteger downstreamCalls = new AtomicInteger();
    private DisposableServer downstream;
    private ReactiveWebServerApplicationContext gateway;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        snapshots.set(snapshot(1, true, defaultHeaders(), ordinaryTimeout()));
        downstream = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) -> {
            downstreamCalls.incrementAndGet();
            if (request.uri().startsWith("/public/slow")) {
                return Mono.delay(Duration.ofSeconds(2))
                        .then(response.header("Content-Type", "text/plain")
                                .sendString(Mono.just("late;app=" + request.requestHeaders().get("X-App-Id")))
                                .then());
            }
            String body = "app=" + request.requestHeaders().get("X-App-Id")
                    + ";platform=" + request.requestHeaders().get("X-App-Platform")
                    + ";user=" + request.requestHeaders().get("X-User-Id")
                    + ";client=" + request.requestHeaders().get("X-Client-Id")
                    + ";session=" + request.requestHeaders().get("X-Session-Id")
                    + ";cookies=" + request.requestHeaders().get("Cookie")
                    + ";authorization=" + request.requestHeaders().get("Authorization")
                    + ";custom=" + request.requestHeaders().get("X-GW-App")
                    + ";version=" + request.requestHeaders().get("X-GW-Version");
            return response.header("Content-Type", "text/plain")
                    .header("Cache-Control", "public, max-age=60").sendString(Mono.just(body));
        }).bindNow();
        gateway = (ReactiveWebServerApplicationContext) new SpringApplicationBuilder(PipelineApplication.class)
                .web(WebApplicationType.REACTIVE)
                .initializers(context -> context.getBeanFactory().registerSingleton(
                        "policySnapshotProvider", (AccessSnapshotCapturer) snapshots::get))
                .properties(Map.of(
                        "server.port", "0",
                        "spring.application.name", "gateway-pipeline-it",
                        "spring.main.banner-mode", "off",
                        "spring.cloud.nacos.config.import-check.enabled", "false",
                        "spring.cloud.nacos.config.enabled", "false",
                        "spring.cloud.nacos.discovery.enabled", "false",
                        "spring.config.location", "optional:classpath:/gateway-pipeline-it-unused.properties",
                        "pipeline.downstream-uri", "http://127.0.0.1:" + downstream.port(),
                        "logging.level.root", "WARN"))
                .run();
        baseUrl = "http://127.0.0.1:" + gateway.getWebServer().getPort();
    }

    @AfterEach
    void tearDown() {
        if (gateway != null) {
            gateway.close();
        }
        if (downstream != null) {
            downstream.disposeNow();
        }
    }

    @Test
    void shouldForwardPublicAppAndOverwriteSpoofedIdentityWhileStrippingCredentials() {
        Reply reply = get("/public/echo", Map.of(
                "X-App-Id", APP.value(), "X-App-Platform", "forged", "X-User-Id", "forged-user",
                "X-Session-Id", "forged-session", "X-Channel-Code", "forged-channel",
                "Cookie", "OTHER=forged", "Authorization", "Basic forged"));
        assertEquals(200, reply.status());
        assertTrue(reply.body().contains("app=public-app;platform=browser"));
        assertTrue(reply.body().contains("user=null;client=null;session=null;cookies=null;authorization=null"));
        assertFalse(reply.body().contains("forged"));
        assertEquals(1, downstreamCalls.get());
    }

    @Test
    void shouldRejectInvalidBrowserIdentityOnAnAnonymousEndpoint() {
        Reply reply = get("/public/echo", Map.of("X-App-Id", APP.value(), "Cookie", "BROWSER_SESSION=forged"));
        assertEquals(401, reply.status());
        assertEquals(0, downstreamCalls.get(), "An invalid identity must not fall back to anonymous access");
    }

    @Test
    void shouldForwardEveryRequestAndEnforceCurrentAppAccessPolicy() {
        Reply first = get("/public/cache", Map.of("X-App-Id", APP.value()));
        Reply second = get("/public/cache", Map.of("X-App-Id", APP.value()));
        assertEquals(200, first.status());
        assertEquals(200, second.status());
        assertEquals(2, downstreamCalls.get(), "v1 has no response cache: every admitted request is forwarded");
        snapshots.set(snapshot(2, false, defaultHeaders(), ordinaryTimeout()));
        assertEquals(400, get("/public/cache", Map.of("X-App-Id", APP.value())).status());
        assertEquals(2, downstreamCalls.get(), "A rejected request must never reach the downstream");
    }

    @Test
    void shouldApplyHeaderSelectionFromNewSnapshotWithoutRestart() throws InterruptedException {
        assertTrue(get("/public/echo", Map.of("X-App-Id", APP.value())).body().contains("app=public-app"));
        var headers = new HeaderConfig(Set.of(),
                Map.of("X-GW-Version", "v2"), Set.of(), CredentialForwarding.stripAll());
        snapshots.set(snapshot(2, true, headers, ordinaryTimeout()));
        Reply changed = get("/public/echo", Map.of("X-App-Id", APP.value(), "X-App-Platform", "forged"));
        assertEquals(200, changed.status());
        assertTrue(changed.body().contains("app=null"));
        assertTrue(changed.body().contains("custom=null;version=v2"));
        assertFalse(changed.body().contains("forged"));
        System.out.printf("GatewayPipelineIT observation: curl -i -H 'X-App-Id: public-app' %s/public/echo%n", baseUrl);
        long seconds = Math.min(60, Math.max(0, Long.getLong(OBSERVE_SECONDS, 0L)));
        if (seconds > 0) {
            TimeUnit.SECONDS.sleep(seconds);
        }
    }

    @Test
    void shouldApplyTimeoutFromTheCurrentSnapshot() {
        snapshots.set(snapshot(2, true, defaultHeaders(), new TimeoutConfig(
                Duration.ofSeconds(1), Duration.ofMillis(100), Duration.ofMillis(500))));
        Reply slow = get("/public/slow", Map.of("X-App-Id", APP.value()));
        assertEquals(504, slow.status(), "The configured timeout must stop the slow downstream");
        assertEquals(1, downstreamCalls.get());
    }

    @Test
    void shouldRejectEndpointWhoseRouteIdDoesNotMatchTheDeliveredScgRoute() {
        snapshots.set(snapshot(2, true, defaultHeaders(), ordinaryTimeout(), "another-route"));
        assertEquals(404, get("/public/echo", Map.of("X-App-Id", APP.value())).status());
        assertEquals(0, downstreamCalls.get());
    }

    @Test
    void shouldRejectUnknownEndpointAndMissingAppBeforeDownstream() {
        assertEquals(404, get("/public/unknown", Map.of("X-App-Id", APP.value())).status());
        assertEquals(400, get("/public/echo", Map.of()).status());
        assertEquals(0, downstreamCalls.get());
    }

    private Reply get(String path, Map<String, String> headers) {
        return HttpClient.create().headers(values -> headers.forEach(values::set))
                .get().uri(baseUrl + path)
                .responseSingle((response, body) -> body.asString().defaultIfEmpty("")
                        .map(text -> new Reply(response.status().code(), text)))
                .block(Duration.ofSeconds(10));
    }

    private static RevisionedAccessSnapshot snapshot(long version, boolean appEnabled,
                                                     HeaderConfig headers, TimeoutConfig timeout) {
        return snapshot(version, appEnabled, headers, timeout, ROUTE);
    }

    private static RevisionedAccessSnapshot snapshot(long version, boolean appEnabled,
                                                     HeaderConfig headers, TimeoutConfig timeout,
                                                     String endpointRouteId) {
        var endpoints = new ArrayList<Endpoint>();
        for (String name : List.of("echo", "cache", "slow")) {
            endpoints.add(new Endpoint(
                    new EndpointId("PUBLIC." + name.toUpperCase(Locale.ROOT)),
                    new ServiceId(SERVICE),
                    new RouteId(endpointRouteId),
                    List.of(new EndpointMatcher("GET", "/public/" + name)),
                    Set.of("public-read"),
                    EndpointStatus.ENABLED,
                    new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                            SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of())));
        }
        var baseId = new CapabilityBundleId("base");
        var base = new CapabilityBundle(baseId, java.util.List.of(timeout, headers));
        var policies = new CapabilityPolicyConfig(java.util.List.of(base), List.of(
                new CapabilityBinding(new CapabilityBindingId("base"), baseId, new ServiceId(SERVICE),
                        null, Set.of(), null, null, null)));
        var appRegistry = new AppRegistry(List.of(new App(APP, "browser",
                appEnabled ? AppStatus.ENABLED : AppStatus.DISABLED, new AppAccess(Set.of()))));
        var content = new IngressContent(appRegistry, new ClientRegistry(List.of()),
                new EndpointRegistry(endpoints), policies);
        return new RevisionedAccessSnapshot(new IngressSnapshot(
                new SnapshotVersion(version), content, "pipeline-" + version, Instant.now()),
                new com.cloud.gateway.domain.ingress.snapshot.Revision(Long.toString(version)));
    }

    private static HeaderConfig defaultHeaders() {
        return HeaderConfig.defaults(CredentialForwarding.stripAll());
    }

    private static TimeoutConfig ordinaryTimeout() {
        return new TimeoutConfig(Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(4));
    }

    private record Reply(int status, String body) { }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({ConfigurationPropertiesAutoConfiguration.class, JacksonAutoConfiguration.class,
            CodecsAutoConfiguration.class, ReactorAutoConfiguration.class, SslAutoConfiguration.class,
            ReactiveWebServerFactoryAutoConfiguration.class, HttpHandlerAutoConfiguration.class,
            WebFluxAutoConfiguration.class, GatewayAutoConfiguration.class})
    @Import(IngressWebFluxConfiguration.class)
    static class PipelineApplication {
        @Bean com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfigValidator capabilityValidator() {
            return com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog.validator();
        }
        @Bean com.cloud.gateway.domain.capability.policy.CapabilityPolicyAssembler capabilityAssembler(
                com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfigValidator validator) {
            return com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog.assembler(validator);
        }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean
        QuotaAcquirer rateLimitQuota() {
            return (rule, value) -> RateLimitDecision.allowed(rule.dimension(), value, 1);
        }
        @Bean QuotaCommandService quotaCommandService(QuotaAcquirer quota) {
            return new QuotaCommandService(quota, quota::tryAcquire, QuotaObserver.noop());
        }
        @Bean ClientCredentialAuthenticator clientCredentialAuthenticator() {
            return (credentials, clientId, evidence, now) ->
                    CompletableFuture.completedFuture(ClientVerificationOutcome.rejected());
        }
        @Bean BrowserSessionVerifier browserSessionVerifier() {
            return token -> CompletableFuture.completedFuture(BrowserSessionVerificationOutcome.invalid());
        }
        @Bean RouteLocator pipelineRoutes(RouteLocatorBuilder builder, Environment environment) {
            return builder.routes().route(ROUTE, predicate -> predicate.path("/public/**")
                    .uri(environment.getRequiredProperty("pipeline.downstream-uri"))).build();
        }
    }
}
