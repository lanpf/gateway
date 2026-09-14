package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.access.definition.EndpointAccessDefinitionRepository;
import com.cloud.gateway.application.access.definition.VersionedAccessDefinition;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;

public final class PropertiesEndpointAccessDefinitionRepository implements com.cloud.gateway.application.access.definition.EndpointAccessDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<EndpointRegistry> source;

    public PropertiesEndpointAccessDefinitionRepository(
            GatewayPolicyDefinitionProperties properties,
            StoredAccessSnapshotMapper mapper,
            StoredPolicyDefinitionCodec codec
    ) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainEndpointRegistry(properties.toStoredEndpoints()),
                codec::encodeEndpointRegistry);
    }

    @Override public com.cloud.gateway.application.access.definition.VersionedAccessDefinition<EndpointRegistry> load() { return source.load(); }
}
