package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.domain.policy.execution.CapabilityExecution;
import com.cloud.gateway.domain.policy.execution.CapabilityOutcome;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Projects typed results without embedding capability selection in either ingress filter. */
public final class CapabilityResponseWriter {
    private final Map<Class<?>, CapabilityOutcomeWriter<?>> writers;

    public CapabilityResponseWriter(List<CapabilityOutcomeWriter<?>> writers) {
        Map<Class<?>, CapabilityOutcomeWriter<?>> registered = new HashMap<>();
        for (CapabilityOutcomeWriter<?> writer : writers) {
            if (registered.putIfAbsent(writer.outcomeType(), writer) != null) {
                throw new IllegalArgumentException("duplicate capability outcome writer");
            }
        }
        this.writers = Map.copyOf(registered);
    }

    public Mono<Boolean> respond(ServerWebExchange exchange, CapabilityExecution execution) {
        return Mono.defer(() -> {
            for (CapabilityOutcome outcome : execution.outcomes()) {
                if (!outcome.allowed()) {
                    CapabilityOutcomeWriter<?> writer = writers.get(outcome.getClass());
                    if (writer != null) {
                        return writer.respondOutcome(exchange, outcome);
                    }
                    exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                    return exchange.getResponse().setComplete().thenReturn(false);
                }
            }
            return Mono.just(true);
        });
    }
}
