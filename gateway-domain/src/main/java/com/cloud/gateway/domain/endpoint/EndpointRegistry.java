package com.cloud.gateway.domain.endpoint;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.gateway.domain.common.AbstractAggregateRootRegistry;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class EndpointRegistry extends AbstractAggregateRootRegistry<Endpoint, EndpointId> {

    public EndpointRegistry(Collection<Endpoint> endpoints) {
        super(endpoints);
        Set<EndpointMatcher> noDuplicates = new HashSet<>();
        NullSafe.forEach(values(), endpoint -> {
            NullSafe.forEach(endpoint.getMatchers(), matcher -> {
                if (!noDuplicates.add(matcher)) {
                    throw ambiguous();
                }
            });
        });
    }

    @Override
    protected DomainException ambiguous() {
        return new DomainException(DomainError.ENDPOINT_AMBIGUOUS);
    }

}
