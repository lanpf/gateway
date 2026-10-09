package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.domain.endpoint.Endpoint;
import org.springframework.core.Ordered;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;

/** Local Admin controllers also require the same authenticated context as proxy routes. */
@RequiredArgsConstructor
public final class LocalPolicyWebFilter implements WebFilter, Ordered {
    private final IngressAdmissionGlobalFilter access;
    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Endpoint endpoint = exchange.getAttribute(GatewayRequestAttributes.ENDPOINT);
        if (endpoint != null && GatewayRequestAttributes.LOCAL_ROUTE_ID.equals(endpoint.getRouteId().value())) {
            return access.filter(exchange, chain::filter);
        }
        return chain.filter(exchange);
    }
    @Override public int getOrder() { return 0; }
}
