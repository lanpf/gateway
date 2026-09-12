package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.policy.EndpointRegistryDefinitionRepository;
import com.cloud.gateway.application.policy.VersionedPolicyDefinition;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;

public final class PropertiesEndpointRegistryDefinitionRepository implements EndpointRegistryDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<EndpointRegistry> source;

    public PropertiesEndpointRegistryDefinitionRepository(
            GatewayPolicyDefinitionProperties properties,
            StoredPolicySnapshotMapper mapper,
            StoredPolicyDefinitionCodec codec
    ) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainEndpointRegistry(properties.toStoredEndpoints()),
                codec::encodeEndpointRegistry);
    }

    @Override public VersionedPolicyDefinition<EndpointRegistry> load() { return source.load(); }
}
