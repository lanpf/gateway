package com.cloud.gateway.domain.endpoint;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.validation.Require;
import com.cloud.framework.domain.AggregateRoot;

import java.util.List;
import java.util.Set;

public record Endpoint(
        EndpointId endpointId,
        ServiceId serviceId,
        RouteId routeId,
        List<EndpointMatcher> matchers,
        Set<String> tags,
        EndpointStatus status,
        EndpointAccess access
) implements AggregateRoot<EndpointId>
{

    public Endpoint {
        Require.notNull(endpointId, DomainException::missingField);
        Require.notNull(serviceId, DomainException::missingField);
        Require.notNull(routeId, DomainException::missingField);

        Require.notEmpty(matchers, DomainException::invalidObjectField).forEach(value -> Require.notNull(value, DomainException::missingField));
        matchers = List.copyOf(matchers);

        tags = tags == null ? Set.of() : tags;
        tags.forEach(value -> Require.notBlank(value, DomainException::invalidObjectField));
        tags = Set.copyOf(tags);

        Require.notNull(status, DomainException::missingField);
        Require.notNull(access, DomainException::missingField);
    }

    @Override
    public EndpointId getId() {
        return endpointId();
    }

    public boolean enabled() { return status == EndpointStatus.ENABLED; }
}
