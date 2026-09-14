package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.access.definition.AppAccessDefinitionRepository;
import com.cloud.gateway.application.access.definition.VersionedAccessDefinition;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import java.util.Map;

public final class PropertiesAppAccessDefinitionRepository implements com.cloud.gateway.application.access.definition.AppAccessDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<Map<AppId, AppAccess>> source;

    public PropertiesAppAccessDefinitionRepository(GatewayPolicyDefinitionProperties properties,
            StoredAccessSnapshotMapper mapper, StoredPolicyDefinitionCodec codec) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainApps(properties.toStoredApps()), codec::encodeApps);
    }

    @Override public com.cloud.gateway.application.access.definition.VersionedAccessDefinition<Map<AppId, AppAccess>> load() {
        return source.load();
    }
}
