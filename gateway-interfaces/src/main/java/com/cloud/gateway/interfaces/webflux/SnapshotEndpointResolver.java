package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.application.ingress.EndpointMatchSource;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.Endpoint;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Optional;

final class SnapshotEndpointResolver implements EndpointMatchSource {
    private final PathPatternParser parser = new PathPatternParser();

    Optional<Endpoint> resolve(EndpointRegistry catalog, String httpMethod, String path) {
        return catalog.select(match(catalog, httpMethod, path));
    }

    @Override
    public List<Endpoint> match(EndpointRegistry catalog, String httpMethod, String path) {
        PathContainer requestPath = PathContainer.parsePath(path);
        return catalog.definitions().stream()
                .filter(definition -> definition.matchers().stream().anyMatch(matcher ->
                        matcher.httpMethod().equalsIgnoreCase(httpMethod)
                                && parser.parse(matcher.pathPattern()).matches(requestPath)))
                .toList();
    }
}
