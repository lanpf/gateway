package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.access.definition.WritableEndpointAccessDefinitionRepository;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import java.util.Map;

public final class NacosEndpointAccessDefinitionRepository
        extends AbstractNacosAccessDefinitionRepository<Map<EndpointId, EndpointAccess>>
        implements com.cloud.gateway.application.access.definition.WritableEndpointAccessDefinitionRepository {
    public NacosEndpointAccessDefinitionRepository(NacosConfigOperations operations, NacosPolicyProperties properties,
            StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getRouteAccessDefinitionDataId(),
                codec::decodeEndpointAccess, codec::encodeEndpointAccess);
    }
}
