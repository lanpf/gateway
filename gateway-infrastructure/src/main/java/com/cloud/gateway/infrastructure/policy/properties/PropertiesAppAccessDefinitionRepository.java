package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.AppPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;

public final class PropertiesAppAccessDefinitionRepository implements com.cloud.gateway.application.configuration.definition.AppAccessDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<AppRegistry> source;

    public PropertiesAppAccessDefinitionRepository(GatewayPolicyDefinitionProperties properties,
            StoredAccessSnapshotMapper mapper, AppPropertiesMapper definitions, StoredPolicyDefinitionCodec codec) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainApps(definitions.toStored(properties.getApps())), codec::encodeApps);
    }

    @Override public RevisionedAccessDefinition<AppRegistry> load() { return source.load(); }
}
