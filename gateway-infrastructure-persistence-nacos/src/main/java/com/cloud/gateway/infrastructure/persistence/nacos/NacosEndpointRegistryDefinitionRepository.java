package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.policy.WritableEndpointRegistryDefinitionRepository;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;

public final class NacosEndpointRegistryDefinitionRepository
        extends AbstractNacosPolicyDefinitionRepository<EndpointRegistry>
        implements WritableEndpointRegistryDefinitionRepository {
    public NacosEndpointRegistryDefinitionRepository(NacosConfigOperations operations, NacosPolicyProperties properties,
            StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getEndpointDefinitionDataId(),
                codec::decodeEndpointRegistry, codec::encodeEndpointRegistry);
    }
}
