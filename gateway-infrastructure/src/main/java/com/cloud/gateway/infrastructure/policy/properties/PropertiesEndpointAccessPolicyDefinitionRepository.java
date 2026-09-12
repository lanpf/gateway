package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.policy.EndpointAccessPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.VersionedPolicyDefinition;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import java.util.Map;

public final class PropertiesEndpointAccessPolicyDefinitionRepository
        implements EndpointAccessPolicyDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<Map<EndpointId, EndpointPolicy>> source;

    public PropertiesEndpointAccessPolicyDefinitionRepository(GatewayPolicyDefinitionProperties properties,
            StoredPolicySnapshotMapper mapper, StoredPolicyDefinitionCodec codec) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainEndpointAccess(properties.toStoredRoutes()), codec::encodeEndpointAccess);
    }

    @Override public VersionedPolicyDefinition<Map<EndpointId, EndpointPolicy>> load() {
        return source.load();
    }
}
