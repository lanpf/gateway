package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.application.quota.QuotaCommandService;
import com.cloud.gateway.domain.policy.capability.RateLimitCapability;
import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
import com.cloud.gateway.domain.policy.execution.CapabilityExecution;
import com.cloud.gateway.domain.policy.execution.CapabilityOutcome;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityResponseWriterTest {
    @Test
    void shouldProjectRateLimitDenialWithoutExecutingQuota() {
        RequestQuotaGuard quotas = new RequestQuotaGuard(new QuotaCommandService(
                (rule, key) -> { throw new AssertionError("projection only"); },
                (rule, key) -> { throw new AssertionError("projection only"); }, (rule, decision) -> { }));
        CapabilityResponseWriter writer = new CapabilityResponseWriter(List.of(new RateLimitOutcomeWriter(quotas)));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
        assertFalse(writer.respond(exchange, report(new RateLimitCapability.Outcome(
                RateLimitDecision.rejected(null, null, Duration.ofMillis(1200))))).block());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("2", exchange.getResponse().getHeaders().getFirst("Retry-After"));
    }

    @Test
    void shouldFailClosedForUnknownDenialAndAllowEmptyPlan() {
        CapabilityResponseWriter writer = new CapabilityResponseWriter(List.of());
        MockServerWebExchange denied = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
        assertFalse(writer.respond(denied, report(new Outcome(false))).block());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, denied.getResponse().getStatusCode());
        MockServerWebExchange allowed = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
        assertTrue(writer.respond(allowed, CapabilityExecution.empty()).block());
        assertNull(allowed.getResponse().getStatusCode());
    }

    private record Outcome(boolean allowed) implements CapabilityOutcome { }

    private static CapabilityExecution report(CapabilityOutcome outcome) {
        return new CapabilityExecution(Set.of(), List.of(outcome));
    }
}
