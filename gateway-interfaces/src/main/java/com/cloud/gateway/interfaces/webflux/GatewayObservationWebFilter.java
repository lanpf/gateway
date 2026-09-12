package com.cloud.gateway.interfaces.webflux;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import org.springframework.http.HttpStatusCode;
import lombok.RequiredArgsConstructor;
import java.util.Locale;

/** Measures the entire ingress chain, including authentication and quota short-circuits. */
@RequiredArgsConstructor
public final class GatewayObservationWebFilter implements WebFilter, Ordered {
    private static final String REQUESTS = "gateway.ingress.requests";
    private static final String OUTCOME_TAG = "outcome";
    private static final String STATUS_CLASS_TAG = "status_class";
    private enum Outcome { CANCELLED, ERROR, COMPLETED }
    private final MeterRegistry registry;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (registry == null) {
            return chain.filter(exchange);
        }
        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start(registry);
            return Mono.defer(() -> chain.filter(exchange)).doFinally(signal -> {
                Outcome outcome = signal == SignalType.CANCEL ? Outcome.CANCELLED
                        : signal == SignalType.ON_ERROR ? Outcome.ERROR : Outcome.COMPLETED;
                HttpStatusCode status = exchange.getResponse().getStatusCode();
                int statusFamily = status == null ? (signal == SignalType.ON_ERROR ? 5 : 2) : status.value() / 100;
                sample.stop(registry.timer(REQUESTS, OUTCOME_TAG, outcome.name().toLowerCase(Locale.ROOT),
                        STATUS_CLASS_TAG, statusFamily + "xx"));
            });
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
