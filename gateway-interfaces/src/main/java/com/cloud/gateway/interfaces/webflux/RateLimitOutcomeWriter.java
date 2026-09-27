package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.domain.capability.runtime.RateLimitCapability;
import lombok.RequiredArgsConstructor;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public final class RateLimitOutcomeWriter implements CapabilityOutcomeWriter<RateLimitCapability.Outcome> {
    private final RequestQuotaGuard quotas;

    @Override
    public Class<RateLimitCapability.Outcome> outcomeType() { return RateLimitCapability.Outcome.class; }

    @Override
    public Mono<Boolean> respond(ServerWebExchange exchange, RateLimitCapability.Outcome outcome) {
        return quotas.respond(exchange, outcome.decision());
    }
}
