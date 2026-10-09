package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.domain.capability.resilience.TimeoutConfig;
import java.util.HashMap;
import java.util.Map;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.RouteMetadataUtils;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * Publishes the declared connect and response budgets to the transport.
 *
 * <p>Only the HTTP client can enforce a connect timeout, and Spring Cloud Gateway's client reads both
 * values from the route metadata: {@code connect-timeout} in millis, applied as Netty's
 * {@code CONNECT_TIMEOUT_MILLIS}, and {@code response-timeout} in millis, applied as the client's
 * response timeout. Writing them there is therefore how a per-route budget reaches Netty, and it also
 * overrides the deployment-wide client defaults for this route.
 *
 * <p>The <em>total</em> budget deliberately stays with the caller rather than going into metadata: it
 * has to cover writing the response out, which a client timeout does not. So connect and read are the
 * transport's, and the total is enforced by Reactor around the whole call.
 */
final class RouteTimeoutMetadata {

    private RouteTimeoutMetadata() { }

    static void apply(ServerWebExchange exchange, TimeoutConfig policy) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return;
        }
        // Copy first: the route's own metadata is the deployment's, and rebuilding the route must not
        // drop entries this class knows nothing about.
        Map<String, Object> metadata = new HashMap<>(route.getMetadata());
        // An Integer, because the client reads this one through a number coercion. The narrowing is
        // safe by construction: TimeoutConfig refuses any duration beyond Integer.MAX_VALUE millis,
        // which is what makes this cast total rather than a silent truncation.
        metadata.put(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, Math.toIntExact(policy.connect().toMillis()));
        metadata.put(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, policy.read().toMillis());
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, Route.async()
                .id(route.getId())
                .uri(route.getUri())
                .order(route.getOrder())
                .asyncPredicate(route.getPredicate())
                .filters(route.getFilters())
                .metadata(metadata)
                .build());
    }
}
