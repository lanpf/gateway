package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.infrastructure.policy.ClientPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;

public final class PropertiesClientAccessDefinitionRepository
        implements com.cloud.gateway.application.configuration.definition.ClientAccessDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<ClientRegistry> source;

    public PropertiesClientAccessDefinitionRepository(GatewayPolicyDefinitionProperties properties,
            StoredAccessSnapshotMapper mapper, ClientPropertiesMapper clients,
            StoredPolicyDefinitionCodec codec) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainClients(clients.toStored(properties.getClients())),
                codec::encodeClients);
    }

    @Override public RevisionedAccessDefinition<ClientRegistry> load() { return source.load(); }
}
