package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.endpoint.EndpointRegistry;

public interface WritableEndpointRegistryDefinitionRepository
        extends EndpointRegistryDefinitionRepository, WritablePolicyDefinitionRepository<EndpointRegistry> {
}
