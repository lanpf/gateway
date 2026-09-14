package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.access.definition.ClientAccessDefinitionRepository;
import com.cloud.gateway.application.access.definition.VersionedAccessDefinition;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.infrastructure.policy.ClientPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import java.util.Map;

public final class PropertiesClientAccessDefinitionRepository
        implements com.cloud.gateway.application.access.definition.ClientAccessDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<Map<ClientId, ClientAccess>> source;

    public PropertiesClientAccessDefinitionRepository(GatewayPolicyDefinitionProperties properties,
            StoredAccessSnapshotMapper mapper, ClientPropertiesMapper clients,
            StoredPolicyDefinitionCodec codec) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainClients(clients.toStored(properties.getClients())),
                codec::encodeClients);
    }

    @Override public com.cloud.gateway.application.access.definition.VersionedAccessDefinition<Map<ClientId, ClientAccess>> load() {
        return source.load();
    }
}
