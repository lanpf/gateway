package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.policy.WritableEndpointCatalogDefinitionRepository;
import com.cloud.gateway.domain.endpoint.EndpointCatalog;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;

public final class NacosEndpointCatalogDefinitionRepository
        extends AbstractNacosPolicyDefinitionRepository<EndpointCatalog>
        implements WritableEndpointCatalogDefinitionRepository {
    public NacosEndpointCatalogDefinitionRepository(NacosConfigOperations operations, NacosPolicyProperties properties,
            StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getEndpointDefinitionDataId(),
                codec::decodeEndpointCatalog, codec::encodeEndpointCatalog);
    }
}
