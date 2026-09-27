package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.application.quota.QuotaCommandService;
import com.cloud.gateway.domain.capability.runtime.RateLimitCapability;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.runtime.CapabilityExecution;
import com.cloud.gateway.domain.capability.runtime.CapabilityOutcome;
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
    void shouldFailClosedForUnknownStopAndAllowEmptyPlan() {
        CapabilityResponseWriter writer = new CapabilityResponseWriter(List.of());
        MockServerWebExchange denied = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
        assertFalse(writer.respond(denied, report(new Outcome(CapabilityOutcome.Disposition.STOP))).block());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, denied.getResponse().getStatusCode());
        MockServerWebExchange allowed = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
        assertTrue(writer.respond(allowed, CapabilityExecution.empty()).block());
        assertNull(allowed.getResponse().getStatusCode());
        MockServerWebExchange continuing = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
        assertTrue(writer.respond(continuing, report(new Outcome(CapabilityOutcome.Disposition.CONTINUE))).block());
        assertNull(continuing.getResponse().getStatusCode());
    }

    private record Outcome(CapabilityOutcome.Disposition disposition) implements CapabilityOutcome {
    }

    @Test
    void shouldStopAfterSuccessfulResponseRegardlessOfWriterContinuationFlag() {
        CapabilityResponseWriter writer = new CapabilityResponseWriter(List.of(new SuccessfulOutcomeWriter()));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
        assertFalse(writer.respond(exchange, report(new Outcome(CapabilityOutcome.Disposition.STOP))).block());
        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
        assertTrue(exchange.getResponse().isCommitted());
    }

    private static final class SuccessfulOutcomeWriter implements CapabilityOutcomeWriter<Outcome> {
        @Override
        public Class<Outcome> outcomeType() { return Outcome.class; }

        @Override
        public reactor.core.publisher.Mono<Boolean> respond(
                org.springframework.web.server.ServerWebExchange exchange, Outcome outcome) {
            exchange.getResponse().setStatusCode(HttpStatus.OK);
            return exchange.getResponse().setComplete().thenReturn(true);
        }
    }

    private static CapabilityExecution report(CapabilityOutcome outcome) {
        return new CapabilityExecution(Set.of(), List.of(outcome));
    }
}
