package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.EndpointPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;

public final class PropertiesEndpointAccessDefinitionRepository implements com.cloud.gateway.application.configuration.definition.EndpointAccessDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<EndpointRegistry> source;

    public PropertiesEndpointAccessDefinitionRepository(
            GatewayPolicyDefinitionProperties properties,
            StoredAccessSnapshotMapper mapper, EndpointPropertiesMapper definitions,
            StoredPolicyDefinitionCodec codec
    ) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainEndpointRegistry(definitions.toStored(properties.getEndpoints())),
                codec::encodeEndpointRegistry);
    }

    @Override public RevisionedAccessDefinition<EndpointRegistry> load() { return source.load(); }
}
