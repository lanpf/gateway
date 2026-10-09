package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.application.ingress.EndpointMatchSource;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.Endpoint;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/** WebFlux path syntax adapter, exposed for runtime assembly of the preparation stage. */
public final class SnapshotEndpointMatchSource implements EndpointMatchSource {
    private final PathPatternParser parser = new PathPatternParser();

    @Override
    public List<Endpoint> match(EndpointRegistry registry, String httpMethod, String path) {
        PathContainer requestPath = PathContainer.parsePath(path);
        return registry.values().stream()
                .filter(endpoint -> endpoint.getMatchers().stream().anyMatch(
                        matcher -> matcher.httpMethod().equalsIgnoreCase(httpMethod)
                                && parser.parse(matcher.pathPattern()).matches(requestPath)))
                .toList();
    }
}
