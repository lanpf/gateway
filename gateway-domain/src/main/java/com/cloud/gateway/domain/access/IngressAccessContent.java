package com.cloud.gateway.domain.access;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.policy.bundle.PolicyConfig;

public record IngressAccessContent(
        AppRegistry appRegistry,
        ClientRegistry clientRegistry,
        EndpointRegistry endpointRegistry,
        PolicyConfig policies
) {
    public IngressAccessContent {
        Require.notNull(appRegistry, DomainException::missingField);
        Require.notNull(clientRegistry, DomainException::missingField);
        Require.notNull(endpointRegistry, DomainException::missingField);
        Require.notNull(policies, DomainException::missingField);
    }
}
