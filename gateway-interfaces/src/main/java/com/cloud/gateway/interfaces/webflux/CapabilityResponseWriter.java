package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.domain.capability.runtime.CapabilityExecution;
import com.cloud.gateway.domain.capability.runtime.CapabilityOutcome;
import com.cloud.framework.core.validation.Require;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Projects typed results without embedding capability selection in either ingress filter. */
public final class CapabilityResponseWriter {
    private final Map<Class<?>, CapabilityOutcomeWriter<?>> writers;

    public CapabilityResponseWriter(List<CapabilityOutcomeWriter<?>> writers) {
        this.writers = Map.copyOf(Require.noDuplicates(writers, CapabilityOutcomeWriter::outcomeType));
    }

    public Mono<Boolean> respond(ServerWebExchange exchange, CapabilityExecution execution) {
        return Mono.defer(() -> {
            for (CapabilityOutcome outcome : execution.outcomes()) {
                if (outcome.disposition() != CapabilityOutcome.Disposition.CONTINUE) {
                    CapabilityOutcomeWriter<?> writer = writers.get(outcome.getClass());
                    if (writer != null) {
                        // The outcome owns flow control; its writer cannot resume a stopped chain.
                        return writer.respondOutcome(exchange, outcome).thenReturn(false);
                    }
                    exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                    return exchange.getResponse().setComplete().thenReturn(false);
                }
            }
            return Mono.just(true);
        });
    }
}
