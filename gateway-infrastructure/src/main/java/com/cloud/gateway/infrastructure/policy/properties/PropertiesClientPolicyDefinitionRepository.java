package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.policy.ClientPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.VersionedPolicyDefinition;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientPolicy;
import com.cloud.gateway.infrastructure.policy.ClientPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import java.util.Map;

public final class PropertiesClientPolicyDefinitionRepository
        implements ClientPolicyDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<Map<ClientId, ClientPolicy>> source;

    public PropertiesClientPolicyDefinitionRepository(GatewayPolicyDefinitionProperties properties,
            StoredPolicySnapshotMapper mapper, ClientPropertiesMapper clients,
            StoredPolicyDefinitionCodec codec) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainClients(clients.toStored(properties.getClients())),
                codec::encodeClients);
    }

    @Override public VersionedPolicyDefinition<Map<ClientId, ClientPolicy>> load() {
        return source.load();
    }
}
