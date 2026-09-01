package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.endpoint.EndpointCatalog;

public interface WritableEndpointCatalogDefinitionRepository
        extends EndpointCatalogDefinitionRepository, WritablePolicyDefinitionRepository<EndpointCatalog> {
}
