package com.cloud.gateway.domain.endpoint;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import java.util.List;

public record EndpointDefinition(
        EndpointId endpointId,
        String ownerService,
        List<EndpointMatcher> matchers,
        String deliveryRouteId
) {
    public EndpointDefinition {
        Require.notNull(endpointId, DomainException::missingField);
        ownerService = Require.notBlank(ownerService, DomainException::missingField).trim();
        matchers = List.copyOf(Require.notEmpty(matchers, DomainException::missingField));
        deliveryRouteId = Require.notBlank(deliveryRouteId, DomainException::missingField).trim();
    }
}
