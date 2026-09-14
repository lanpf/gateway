package com.cloud.gateway.application.access.definition;

import com.cloud.gateway.domain.endpoint.EndpointRegistry;

public interface WritableEndpointAccessDefinitionRepository
        extends EndpointAccessDefinitionRepository, WritableAccessDefinitionRepository<EndpointRegistry> {
}
