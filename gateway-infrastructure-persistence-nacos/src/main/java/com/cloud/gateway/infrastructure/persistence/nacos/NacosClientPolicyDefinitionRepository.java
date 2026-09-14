package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.access.definition.WritableClientAccessDefinitionRepository;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import java.util.Map;

public final class NacosClientAccessDefinitionRepository
        extends AbstractNacosAccessDefinitionRepository<Map<ClientId, ClientAccess>>
        implements com.cloud.gateway.application.access.definition.WritableClientAccessDefinitionRepository {
    public NacosClientAccessDefinitionRepository(NacosConfigOperations operations,
            NacosPolicyProperties properties, StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getClientDefinitionDataId(),
                codec::decodeClients, codec::encodeClients);
    }
}
