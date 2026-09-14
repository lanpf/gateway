package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.access.definition.EndpointAccessDefinitionRepository;
import com.cloud.gateway.application.access.definition.VersionedAccessDefinition;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import java.util.Map;

public final class PropertiesEndpointAccessDefinitionRepository
        implements com.cloud.gateway.application.access.definition.EndpointAccessDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<Map<EndpointId, EndpointAccess>> source;

    public PropertiesEndpointAccessDefinitionRepository(GatewayPolicyDefinitionProperties properties,
            StoredAccessSnapshotMapper mapper, StoredPolicyDefinitionCodec codec) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainEndpointAccess(properties.toStoredRoutes()), codec::encodeEndpointAccess);
    }

    @Override public com.cloud.gateway.application.access.definition.VersionedAccessDefinition<Map<EndpointId, EndpointAccess>> load() {
        return source.load();
    }
}
