package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.policy.WritableRouteAccessPolicyDefinitionRepository;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import java.util.Map;

public final class NacosRouteAccessPolicyDefinitionRepository
        extends AbstractNacosPolicyDefinitionRepository<Map<EndpointId, IngressRouteAccessPolicy>>
        implements WritableRouteAccessPolicyDefinitionRepository {
    public NacosRouteAccessPolicyDefinitionRepository(NacosConfigOperations operations, NacosPolicyProperties properties,
            StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getRouteAccessDefinitionDataId(),
                codec::decodeRouteAccess, codec::encodeRouteAccess);
    }
}
