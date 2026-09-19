package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.configuration.definition.WritableClientAccessDefinitionRepository;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;

public final class NacosClientAccessDefinitionRepository
        extends AbstractNacosAccessDefinitionRepository<ClientRegistry>
        implements WritableClientAccessDefinitionRepository {
    public NacosClientAccessDefinitionRepository(NacosConfigOperations operations,
            NacosPolicyProperties properties, StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getClientDefinitionDataId(),
                codec::decodeClients, codec::encodeClients);
    }
}
