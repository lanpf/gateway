package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.policy.WritableClientPolicyDefinitionRepository;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientPolicy;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import java.util.Map;

public final class NacosClientPolicyDefinitionRepository
        extends AbstractNacosPolicyDefinitionRepository<Map<ClientId, ClientPolicy>>
        implements WritableClientPolicyDefinitionRepository {
    public NacosClientPolicyDefinitionRepository(NacosConfigOperations operations,
            NacosPolicyProperties properties, StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getClientDefinitionDataId(),
                codec::decodeClients, codec::encodeClients);
    }
}
