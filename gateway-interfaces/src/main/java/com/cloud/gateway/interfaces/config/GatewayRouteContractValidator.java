package com.cloud.gateway.interfaces.config;

import com.cloud.gateway.interfaces.webflux.GatewayRequestAttributes;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer;
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
    private final com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer provider;
    private final RouteLocator routes;
    @Override public void run(ApplicationArguments arguments) {
        Set<String> ids = routes.getRoutes().map(Route::getId).collect(Collectors.toSet()).block(Duration.ofSeconds(10));
        if (ids == null) {
            throw new IllegalStateException("Gateway route discovery did not complete");
        }
        for (Endpoint endpoint : provider.capture().snapshot().content().endpointRegistry().values()) {
            String routeId = endpoint.routeId().value();
            if (!GatewayRequestAttributes.LOCAL_ROUTE_ID.equals(routeId) && !ids.contains(routeId)) {
                throw new IllegalStateException("Registered endpoint references an unavailable gateway route");
            }
        }
    }
}
