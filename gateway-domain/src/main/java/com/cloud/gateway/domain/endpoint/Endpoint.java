package com.cloud.gateway.domain.endpoint;

import com.cloud.framework.core.validation.Require;
import com.cloud.framework.domain.AggregateRoot;
import com.cloud.gateway.domain.common.DomainException;

import java.util.List;
import java.util.Set;

public record Endpoint(
        EndpointId endpointId,
        ServiceId serviceId,
        RouteId routeId,
        List<EndpointMatcher> matchers,
        Set<String> tags,
        EndpointStatus status,
        EndpointPolicy accessPolicy
) implements AggregateRoot<EndpointId>
{

    public Endpoint {
        Require.notNull(endpointId, DomainException::missingField);
        Require.notNull(serviceId, DomainException::missingField);
        Require.notNull(routeId, DomainException::missingField);

        Require.notEmpty(matchers, DomainException::missingField);
        matchers.forEach(matcher -> Require.notNull(matcher, DomainException::invalidField));
        matchers = List.copyOf(matchers);

        tags = tags == null ? Set.of() : tags;
        tags.forEach(tag -> Require.notBlank(tag, DomainException::invalidField));
        tags = Set.copyOf(tags);

        Require.notNull(status, DomainException::missingField);
        Require.notNull(accessPolicy, DomainException::missingField);
    }

    @Override
    public EndpointId getId() {
        return endpointId();
    }

    public boolean enabled() { return status == EndpointStatus.ENABLED; }
}
