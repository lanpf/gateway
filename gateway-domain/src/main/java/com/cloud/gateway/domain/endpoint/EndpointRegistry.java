package com.cloud.gateway.domain.endpoint;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.AggregateRootRegistry;
import com.cloud.gateway.domain.common.DomainError;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class EndpointRegistry extends AggregateRootRegistry<Endpoint, EndpointId> {

    public EndpointRegistry(Collection<Endpoint> endpoints) {
        super(endpoints);
        Set<EndpointMatcher> indexed = new LinkedHashSet<>();
        values().forEach(endpoint ->
                Require.notNull(endpoint, DomainException::missingField).matchers().forEach(matcher -> {
                    if (!indexed.add(matcher)) {
                        throw ambiguous();
                    }
        }));
    }

    @Override
    protected DomainException ambiguous() {
        return new DomainException(DomainError.ENDPOINT_AMBIGUOUS);
    }

}
