package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.policy.WritableEndpointAccessPolicyDefinitionRepository;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import java.util.Map;

public final class NacosEndpointAccessPolicyDefinitionRepository
        extends AbstractNacosPolicyDefinitionRepository<Map<EndpointId, EndpointPolicy>>
        implements WritableEndpointAccessPolicyDefinitionRepository {
    public NacosEndpointAccessPolicyDefinitionRepository(NacosConfigOperations operations, NacosPolicyProperties properties,
            StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getRouteAccessDefinitionDataId(),
                codec::decodeEndpointAccess, codec::encodeEndpointAccess);
    }
}
