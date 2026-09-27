package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.domain.capability.configuration.CapabilityComposition;
import com.cloud.gateway.infrastructure.policy.CapabilityCompositionPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;

public final class PropertiesCapabilityCompositionDefinitionRepository implements com.cloud.gateway.application.configuration.definition.CapabilityCompositionDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<CapabilityComposition> source;
    public PropertiesCapabilityCompositionDefinitionRepository(GatewayPolicyDefinitionProperties properties,
            StoredAccessSnapshotMapper mapper, CapabilityCompositionPropertiesMapper policyConfiguration,
            StoredPolicyDefinitionCodec codec) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainPolicies(policyConfiguration.toStored(properties.getPolicies())),
                codec::encodeCapabilityComposition);
    }
    @Override public RevisionedAccessDefinition<CapabilityComposition> load() { return source.load(); }
}
