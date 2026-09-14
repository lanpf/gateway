package com.cloud.gateway.interfaces.config;

import com.cloud.gateway.interfaces.webflux.GatewayRequestAttributes;
import com.cloud.gateway.application.access.publication.AccessSnapshotProvider;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import com.cloud.gateway.domain.endpoint.Endpoint;
import lombok.RequiredArgsConstructor;

/** Fails startup when a registered endpoint cannot be delivered by this gateway. */
@RequiredArgsConstructor
public final class GatewayRouteContractValidator implements ApplicationRunner {
    private final com.cloud.gateway.application.access.publication.AccessSnapshotProvider provider;
    private final RouteLocator routes;
    @Override public void run(ApplicationArguments arguments) {
        Set<String> ids = routes.getRoutes().map(Route::getId).collect(Collectors.toSet()).block(Duration.ofSeconds(10));
        if (ids == null) {
            throw new IllegalStateException("Gateway route discovery did not complete");
        }
        for (Endpoint endpoint : provider.current().snapshot().content().endpointRegistry().asMap().values()) {
            if (!GatewayRequestAttributes.LOCAL_ROUTE_ID.equals(endpoint.routeId()) && !ids.contains(endpoint.routeId())) {
                throw new IllegalStateException("Registered endpoint references an unavailable gateway route");
            }
        }
    }
}
