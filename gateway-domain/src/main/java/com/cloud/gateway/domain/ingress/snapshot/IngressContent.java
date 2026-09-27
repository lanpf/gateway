package com.cloud.gateway.domain.ingress.snapshot;

import com.cloud.gateway.domain.common.DomainException;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.capability.configuration.CapabilityComposition;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;

public record IngressContent(
        AppRegistry apps,
        ClientRegistry clients,
        EndpointRegistry endpoints,
        CapabilityComposition capabilities
) {
    public IngressContent {
        Require.notNull(apps, DomainException::missingField);
        Require.notNull(clients, DomainException::missingField);
        Require.notNull(endpoints, DomainException::missingField);
        Require.notNull(capabilities, DomainException::missingField);
    }
}
