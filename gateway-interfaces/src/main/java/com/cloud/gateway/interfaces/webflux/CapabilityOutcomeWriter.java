package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.domain.capability.runtime.CapabilityOutcome;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface CapabilityOutcomeWriter<O extends CapabilityOutcome> {
    Class<O> outcomeType();
    Mono<Boolean> respond(ServerWebExchange exchange, O outcome);

    default Mono<Boolean> respondOutcome(ServerWebExchange exchange, CapabilityOutcome outcome) {
        return respond(exchange, outcomeType().cast(outcome));
    }
}
