package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
import com.cloud.gateway.domain.policy.capability.RateLimitQuota;
import com.cloud.gateway.application.quota.QuotaCommandService;
import com.cloud.gateway.application.quota.QuotaObserver;
import com.cloud.gateway.domain.policy.capability.RateLimitDimension;
import com.cloud.gateway.domain.policy.capability.RateLimitMode;
import com.cloud.gateway.domain.policy.capability.RateLimitPolicy;
import com.cloud.gateway.domain.policy.capability.RateLimitRules;
import com.cloud.gateway.domain.policy.capability.RedisFailurePolicy;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

class RequestQuotaGuardTest {
    private static final Duration WAIT = Duration.ofSeconds(5);

    @Test
    void shouldExposeAsyncDecisionWithoutWritingHttpUntilRespondIsSubscribed() {
        RequestQuotaGuard guard = guard((rule, key) -> {
            assertTrue(Thread.currentThread().getName().startsWith("boundedElastic-"));
            return RateLimitDecision.rejected(rule.dimension(), key, Duration.ofMillis(1250));
        });
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"));
        CompletionStage<RateLimitDecision> pending = guard.acquire(List.of(rules()),
                Map.of(RateLimitDimension.IP, "192.0.2.1"), false, new HashSet<>());
        RateLimitDecision decision = Mono.fromCompletionStage(pending).block(WAIT);
        assertNotNull(decision);
        assertNull(exchange.getResponse().getStatusCode());
        Mono<Boolean> response = guard.respond(exchange, decision);
        assertNull(exchange.getResponse().getStatusCode());
        assertEquals(false, response.block(WAIT));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("2", exchange.getResponse().getHeaders().getFirst("Retry-After"));
    }

    @Test
    void shouldKeepAllowLazyAndLeaveAllowedResponseUntouched() {
        AtomicInteger calls = new AtomicInteger();
        RequestQuotaGuard guard = guard((rule, key) -> {
            calls.incrementAndGet();
            return RateLimitDecision.allowed(rule.dimension(), key, 1);
        });
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"));
        Mono<Boolean> result = guard.allow(exchange, List.of(rules()), Map.of(RateLimitDimension.IP, "ip"),
                false, new HashSet<>());
        assertEquals(0, calls.get());
        assertEquals(true, result.block(WAIT));
        assertEquals(2, calls.get());
        assertNull(exchange.getResponse().getStatusCode());
        assertFalse(exchange.getResponse().isCommitted());
    }

    @Test
    void shouldRoundSubsecondRetryUpWithoutOverflow() {
        RequestQuotaGuard guard = guard((rule, key) -> RateLimitDecision.allowed(rule.dimension(), key, 1));
        MockServerWebExchange small = MockServerWebExchange.from(MockServerHttpRequest.get("/"));
        guard.respond(small, RateLimitDecision.rejected(RateLimitDimension.IP, "ip", Duration.ofNanos(1))).block(WAIT);
        assertEquals("1", small.getResponse().getHeaders().getFirst("Retry-After"));
        MockServerWebExchange large = MockServerWebExchange.from(MockServerHttpRequest.get("/"));
        guard.respond(large, RateLimitDecision.rejected(RateLimitDimension.IP, "ip", Duration.ofSeconds(Long.MAX_VALUE))).block(WAIT);
        assertEquals(Long.toString(Long.MAX_VALUE), large.getResponse().getHeaders().getFirst("Retry-After"));
    }

    @Test
    void shouldExecuteEveryRuleAndReturnLongestDelayOffEventLoop() {
        AtomicInteger calls = new AtomicInteger();
        RequestQuotaGuard guard = guard((rule, key) -> {
            assertTrue(Thread.currentThread().getName().startsWith("boundedElastic-"));
            int call = calls.incrementAndGet();
            return RateLimitDecision.rejected(rule.dimension(), key, Duration.ofMillis(call * 1100L));
        });
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"));
        assertEquals(false, guard.allow(exchange, List.of(rules()), Map.of(RateLimitDimension.IP, "192.0.2.1"),
                false, new HashSet<>()).block(WAIT));
        assertEquals(2, calls.get());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("3", exchange.getResponse().getHeaders().getFirst("Retry-After"));
    }

    @Test
    void shouldNotConsumeIpRuleAgainAfterAuthentication() {
        AtomicInteger calls = new AtomicInteger();
        RequestQuotaGuard guard = guard((rule, key) -> {
            calls.incrementAndGet();
            return RateLimitDecision.allowed(rule.dimension(), key, 1);
        });
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"));
        HashSet<String> applied = new HashSet<>();
        assertEquals(true, guard.allow(exchange, List.of(rules()), Map.of(RateLimitDimension.IP, "192.0.2.1"),
                true, applied).block(WAIT));
        assertEquals(true, guard.allow(exchange, List.of(rules()), Map.of(RateLimitDimension.IP, "192.0.2.1"),
                false, applied).block(WAIT));
        assertEquals(2, calls.get());
    }

    @Test
    void shouldPreferUnavailableToAnyRateDenial() {
        RequestQuotaGuard guard = guard((rule, key) -> "one".equals(rule.ruleId())
                ? RateLimitDecision.unavailable(rule.dimension(), key)
                : RateLimitDecision.rejected(rule.dimension(), key, Duration.ofSeconds(10)));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"));
        assertEquals(false, guard.allow(exchange, List.of(rules()), Map.of(RateLimitDimension.IP, "192.0.2.1"),
                false, new HashSet<>()).block(WAIT));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertFalse(exchange.getResponse().getHeaders().containsKey("Retry-After"));
    }

    private static RequestQuotaGuard guard(RateLimitQuota quota) {
        return new RequestQuotaGuard(new QuotaCommandService(quota,
                (rule, key) -> RateLimitDecision.unavailable(rule.dimension(), key), QuotaObserver.noop()));
    }

    private static RateLimitRules rules() {
        return new RateLimitRules(List.of(
                new RateLimitPolicy("one", RateLimitDimension.IP, 10, 1, RateLimitMode.EXACT),
                new RateLimitPolicy("two", RateLimitDimension.IP, 10, 1, RateLimitMode.EXACT)), RedisFailurePolicy.CLOSED);
    }
}
