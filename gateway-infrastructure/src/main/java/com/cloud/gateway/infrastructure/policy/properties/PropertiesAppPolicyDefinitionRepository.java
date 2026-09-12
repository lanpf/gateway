package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.policy.AppPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.VersionedPolicyDefinition;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import java.util.Map;

public final class PropertiesAppPolicyDefinitionRepository implements AppPolicyDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<Map<AppId, AppPolicy>> source;

    public PropertiesAppPolicyDefinitionRepository(GatewayPolicyDefinitionProperties properties,
            StoredPolicySnapshotMapper mapper, StoredPolicyDefinitionCodec codec) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainApps(properties.toStoredApps()), codec::encodeApps);
    }

    @Override public VersionedPolicyDefinition<Map<AppId, AppPolicy>> load() {
        return source.load();
    }
}
