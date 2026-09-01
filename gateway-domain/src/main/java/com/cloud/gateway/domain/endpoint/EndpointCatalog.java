package com.cloud.gateway.domain.endpoint;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class EndpointCatalog {
    private final Map<EndpointId, EndpointDefinition> definitions;

    public EndpointCatalog(Collection<EndpointDefinition> definitions) {
        Require.notNull(definitions, DomainException::missingField);
        Map<EndpointId, EndpointDefinition> indexed = new LinkedHashMap<>();
        Map<EndpointMatcher, EndpointId> matcherOwners = new LinkedHashMap<>();
        definitions.forEach(definition -> {
            EndpointDefinition previous = indexed.putIfAbsent(definition.endpointId(), definition);
            if (previous != null) {
                throw new DomainException(DomainError.ENDPOINT_CATALOG_ALREADY_EXISTS);
            }
            definition.matchers().forEach(matcher -> {
                EndpointId previousOwner = matcherOwners.putIfAbsent(matcher, definition.endpointId());
                if (previousOwner != null) {
                    throw new DomainException(DomainError.ENDPOINT_CATALOG_AMBIGUOUS);
                }
            });
        });
        this.definitions = Map.copyOf(indexed);
    }

    public Optional<EndpointDefinition> find(EndpointId endpointId) {
        return Optional.ofNullable(definitions.get(endpointId));
    }

    public Collection<EndpointDefinition> definitions() {
        return definitions.values();
    }

    public Map<EndpointId, EndpointDefinition> asMap() {
        return definitions;
    }
}
