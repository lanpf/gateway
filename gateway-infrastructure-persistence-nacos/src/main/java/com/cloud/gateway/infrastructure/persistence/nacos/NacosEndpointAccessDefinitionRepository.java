package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.access.definition.WritableEndpointAccessDefinitionRepository;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;

public final class NacosEndpointAccessDefinitionRepository
        extends AbstractNacosAccessDefinitionRepository<EndpointRegistry>
        implements com.cloud.gateway.application.access.definition.WritableEndpointAccessDefinitionRepository {
    public NacosEndpointAccessDefinitionRepository(NacosConfigOperations operations, NacosPolicyProperties properties,
                                                   StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getEndpointDefinitionDataId(),
                codec::decodeEndpointRegistry, codec::encodeEndpointRegistry);
    }
}
