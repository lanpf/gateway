package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.EndpointCatalog;
import com.cloud.gateway.domain.endpoint.EndpointDefinition;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Optional;

final class SnapshotEndpointResolver {
    private final PathPatternParser parser = new PathPatternParser();

    Optional<EndpointDefinition> resolve(EndpointCatalog catalog, String method, String path) {
        PathContainer requestPath = PathContainer.parsePath(path);
        List<EndpointDefinition> matched = catalog.definitions().stream()
                .filter(definition -> definition.matchers().stream().anyMatch(matcher ->
                        matcher.httpMethod().equalsIgnoreCase(method)
                                && parser.parse(matcher.pathPattern()).matches(requestPath)))
                .toList();
        if (matched.size() > 1) {
            throw new DomainException(DomainError.ENDPOINT_CATALOG_AMBIGUOUS);
        }
        return matched.stream().findFirst();
    }
}
