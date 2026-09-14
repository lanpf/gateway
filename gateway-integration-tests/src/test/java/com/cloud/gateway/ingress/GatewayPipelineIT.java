package com.cloud.gateway.ingress;

import com.cloud.gateway.application.access.publication.AccessSnapshotProvider;
import com.cloud.gateway.application.access.publication.VersionedAccessSnapshot;
import com.cloud.gateway.application.security.BrowserSessionVerifier;
import com.cloud.gateway.application.security.BrowserSessionVerificationOutcome;
import com.cloud.gateway.application.security.ClientCredentialAuthenticator;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.policy.bundle.*;
import com.cloud.gateway.domain.policy.capability.*;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.policy.header.*;
import com.cloud.gateway.infrastructure.governance.config.GovernanceAutoConfiguration;
import com.cloud.gateway.interfaces.config.GatewayIngressWebFluxBeans;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

/** Isolated HTTP smoke: production gateway filters and governance, no external user-auth or Nacos. */
class GatewayPipelineIT {
    private static final AppId APP = new AppId("public-app");
    private static final String ROUTE = "pipeline-route";
    private static final String SERVICE = "pipeline-downstream";
    private static final String OBSERVE_SECONDS = "gateway.it.manual-observation-seconds";
    private final AtomicReference<com.cloud.gateway.application.access.publication.VersionedAccessSnapshot> snapshots = new AtomicReference<>();
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
                return Mono.delay(Duration.ofSeconds(2)).then(response.sendString(Mono.just("late")).then());
            }
            String body = "app=" + request.requestHeaders().get("X-App-Id")
                    + ";platform=" + request.requestHeaders().get("X-App-Platform")
                    + ";user=" + request.requestHeaders().get("X-User-Id")
                    + ";client=" + request.requestHeaders().get("X-Client-Id")
                    + ";session=" + request.requestHeaders().get("X-Session-Id")
                    + ";cookies=" + request.requestHeaders().get("Cookie")
                    + ";authorization=" + request.requestHeaders().get("Authorization")
                    + ";custom=" + request.requestHeaders().get("X-Gateway-App")
                    + ";version=" + request.requestHeaders().get("X-Gateway-Version");
            return response.header("Content-Type", "text/plain")
                    .header("Cache-Control", "public, max-age=60").sendString(Mono.just(body));
        }).bindNow();
        gateway = (ReactiveWebServerApplicationContext) new SpringApplicationBuilder(PipelineApplication.class)
                .web(WebApplicationType.REACTIVE)
                .initializers(context -> context.getBeanFactory().registerSingleton(
                        "policySnapshotProvider", (com.cloud.gateway.application.access.publication.AccessSnapshotProvider) snapshots::get))
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
                "Cookie", "BROWSER_SESSION=forged", "Authorization", "Bearer forged"));
        assertEquals(200, reply.status());
        assertTrue(reply.body().contains("app=public-app;platform=browser"));
        assertTrue(reply.body().contains("user=null;client=null;session=null;cookies=null;authorization=null"));
        assertFalse(reply.body().contains("forged"));
        assertEquals(1, downstreamCalls.get());
    }

    @Test
    void shouldServeCacheButStillEnforceNewAppAccessPolicy() {
        Reply first = get("/public/cache", Map.of("X-App-Id", APP.value()));
        Reply second = get("/public/cache", Map.of("X-App-Id", APP.value()));
        assertEquals(200, first.status());
        assertEquals(first.body(), second.body());
        assertEquals(1, downstreamCalls.get(), "Second request should be served without calling downstream");
        snapshots.set(snapshot(2, false, defaultHeaders(), ordinaryTimeout()));
        assertEquals(403, get("/public/cache", Map.of("X-App-Id", APP.value())).status());
        assertEquals(1, downstreamCalls.get(), "Cached content must not bypass current access policy");
    }

    @Test
    void shouldApplyHeaderSelectionFromNewSnapshotWithoutRestart() throws InterruptedException {
        assertTrue(get("/public/echo", Map.of("X-App-Id", APP.value())).body().contains("app=public-app"));
        var headers = new HeaderPolicy(Set.of(),
                Map.of("X-Gateway-Version", "v2"), Set.of(), CredentialForwarding.stripAll());
        snapshots.set(snapshot(2, true, headers, ordinaryTimeout()));
        Reply changed = get("/public/echo", Map.of("X-App-Id", APP.value(), "X-Gateway-App", "forged"));
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
    void shouldReturnGatewayTimeoutForSlowDownstream() {
        snapshots.set(snapshot(2, true, defaultHeaders(), new TimeoutPolicy(
                Duration.ofSeconds(1), Duration.ofMillis(100), Duration.ofMillis(500))));
        assertEquals(504, get("/public/slow", Map.of("X-App-Id", APP.value())).status());
        assertEquals(1, downstreamCalls.get());
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

    private static com.cloud.gateway.application.access.publication.VersionedAccessSnapshot snapshot(long version, boolean appEnabled,
                                                     HeaderPolicy headers, TimeoutPolicy timeout) {
        var definitions = new java.util.ArrayList<Endpoint>();
        var accesses = new java.util.HashMap<EndpointId, EndpointAccess>();
        for (String name : List.of("echo", "cache", "slow")) {
            var id = new EndpointId("PUBLIC." + name.toUpperCase(java.util.Locale.ROOT));
            definitions.add(new Endpoint(id, SERVICE,
                    List.of(new EndpointMatcher("GET", "/public/" + name)), ROUTE, Set.of("public-read")));
            accesses.put(id, new EndpointAccess(id, ClientVerificationRequirement.OPTIONAL,
                    ClientVerificationMode.IGNORE, SubjectAuthenticationRequirement.IGNORE, Set.of(), Set.of(),
                    CredentialForwarding.stripAll(), EndpointStatus.ENABLED));
        }
        var baseId = new PolicyBundleId("base");
        var cacheId = new PolicyBundleId("cache");
        var base = new PolicyBundle(baseId, null, timeout, null, null, headers);
        var cache = new PolicyBundle(cacheId, null, null, null,
                new CachePolicy(Duration.ofSeconds(30), Duration.ZERO, 16384, Set.of()), null);
        var policies = new PolicyConfig(Map.of(baseId, base, cacheId, cache), List.of(
                new PolicyBinding(new PolicyBindingId("base"), baseId, SERVICE, null, Set.of(), null, null, null),
                new PolicyBinding(new PolicyBindingId("cache"), cacheId, null,
                        new EndpointId("PUBLIC.CACHE"), Set.of(), null, null, null)));
        var app = new AppAccess(APP,"browser", Set.of(), appEnabled ? PolicyStatus.ENABLED : PolicyStatus.DISABLED);
        var content = new IngressAccessContent(new EndpointRegistry(definitions),
                Map.of(APP, app), Map.of(), accesses, policies);
        return new com.cloud.gateway.application.access.publication.VersionedAccessSnapshot(new IngressAccessSnapshot(version, content,
                "pipeline-" + version, Instant.now()), Long.toString(version));
    }

    private static HeaderPolicy defaultHeaders() {
        return HeaderPolicy.defaults(CredentialForwarding.stripAll());
    }

    private static TimeoutPolicy ordinaryTimeout() {
        return new TimeoutPolicy(Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(4));
    }

    private record Reply(int status, String body) { }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({ConfigurationPropertiesAutoConfiguration.class, JacksonAutoConfiguration.class,
            CodecsAutoConfiguration.class, ReactorAutoConfiguration.class, SslAutoConfiguration.class,
            ReactiveWebServerFactoryAutoConfiguration.class, HttpHandlerAutoConfiguration.class,
            WebFluxAutoConfiguration.class, GatewayAutoConfiguration.class})
    @Import({GatewayIngressWebFluxBeans.class, GovernanceAutoConfiguration.class})
    static class PipelineApplication {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean RateLimitQuota rateLimitQuota() {
            return (rule, value) -> RateLimitDecision.allowed(rule.dimension(), value, 1);
        }
        @Bean ClientCredentialAuthenticator clientCredentialAuthenticator() {
            return (policy, evidence, now) -> CompletableFuture.completedFuture(ClientVerificationOutcome.rejected());
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
