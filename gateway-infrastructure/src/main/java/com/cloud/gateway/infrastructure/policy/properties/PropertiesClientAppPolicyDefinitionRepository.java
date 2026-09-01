package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.policy.ClientAppPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.VersionedPolicyDefinition;
import com.cloud.gateway.domain.policy.ClientAppId;
import com.cloud.gateway.domain.policy.IngressClientAppPolicy;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.IngressPolicyDefinitionProperties;
import java.util.Map;

public final class PropertiesClientAppPolicyDefinitionRepository implements ClientAppPolicyDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<Map<ClientAppId, IngressClientAppPolicy>> source;

    public PropertiesClientAppPolicyDefinitionRepository(IngressPolicyDefinitionProperties properties,
            StoredPolicySnapshotMapper mapper, StoredPolicyDefinitionCodec codec) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainClientApps(properties.toStoredClientApps()), codec::encodeClientApps);
    }

    @Override public VersionedPolicyDefinition<Map<ClientAppId, IngressClientAppPolicy>> load() {
        return source.load();
    }
}
