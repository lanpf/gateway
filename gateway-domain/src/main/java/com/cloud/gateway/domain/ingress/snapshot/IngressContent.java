package com.cloud.gateway.domain.ingress.snapshot;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.policy.bundle.CapabilityComposition;

public record IngressContent(
        AppRegistry appRegistry,
        ClientRegistry clientRegistry,
        EndpointRegistry endpointRegistry,
        CapabilityComposition capabilityComposition
) {
    public IngressContent {
        Require.notNull(appRegistry, DomainException::missingField);
        Require.notNull(clientRegistry, DomainException::missingField);
        Require.notNull(endpointRegistry, DomainException::missingField);
        Require.notNull(capabilityComposition, DomainException::missingField);
    }
}
