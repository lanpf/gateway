package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.policy.RouteAccessPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.VersionedPolicyDefinition;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.IngressPolicyDefinitionProperties;
import java.util.Map;

public final class PropertiesRouteAccessPolicyDefinitionRepository
        implements RouteAccessPolicyDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<Map<EndpointId, IngressRouteAccessPolicy>> source;

    public PropertiesRouteAccessPolicyDefinitionRepository(IngressPolicyDefinitionProperties properties,
            StoredPolicySnapshotMapper mapper, StoredPolicyDefinitionCodec codec) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainRouteAccess(properties.toStoredRoutes()), codec::encodeRouteAccess);
    }

    @Override public VersionedPolicyDefinition<Map<EndpointId, IngressRouteAccessPolicy>> load() {
        return source.load();
    }
}
