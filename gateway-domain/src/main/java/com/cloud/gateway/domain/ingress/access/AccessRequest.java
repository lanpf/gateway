package com.cloud.gateway.domain.ingress.access;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.EndpointId;

public record AccessRequest(
        EndpointId endpointId,
        AppId appId,
        ClientId clientId,
        String origin,
        boolean unsafe
) {
    public AccessRequest {
        Require.notNull(endpointId, DomainException::missingField);
        Require.notNull(appId, DomainException::missingField);
    }
}
