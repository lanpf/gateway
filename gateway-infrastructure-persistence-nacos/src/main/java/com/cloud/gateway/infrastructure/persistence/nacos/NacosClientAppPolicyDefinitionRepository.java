package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.policy.WritableClientAppPolicyDefinitionRepository;
import com.cloud.gateway.domain.policy.ClientAppId;
import com.cloud.gateway.domain.policy.IngressClientAppPolicy;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import java.util.Map;

public final class NacosClientAppPolicyDefinitionRepository
        extends AbstractNacosPolicyDefinitionRepository<Map<ClientAppId, IngressClientAppPolicy>>
        implements WritableClientAppPolicyDefinitionRepository {
    public NacosClientAppPolicyDefinitionRepository(NacosConfigOperations operations, NacosPolicyProperties properties,
            StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getClientAppDefinitionDataId(),
                codec::decodeClientApps, codec::encodeClientApps);
    }
}
