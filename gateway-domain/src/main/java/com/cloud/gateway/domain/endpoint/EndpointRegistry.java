package com.cloud.gateway.domain.endpoint;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.AggregateRootRegistry;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class EndpointRegistry extends AggregateRootRegistry<Endpoint, EndpointId> {

    public EndpointRegistry(Collection<Endpoint> endpoints) {
        super(endpoints);
        Map<EndpointMatcher, EndpointId> indexed = new LinkedHashMap<>();
        endpoints.forEach(endpoint -> {
            endpoint.matchers().forEach(matcher -> {
                Require.notNull(matcher, DomainException::invalidField);
                if (indexed.putIfAbsent(matcher, endpoint.endpointId()) != null) {
                    throw new DomainException(DomainError.ENDPOINT_AMBIGUOUS);
                }
            });
        });
    }

}
