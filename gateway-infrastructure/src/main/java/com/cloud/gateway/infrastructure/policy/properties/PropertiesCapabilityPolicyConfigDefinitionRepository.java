package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
import com.cloud.gateway.infrastructure.policy.CapabilityPolicyConfigPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;

public final class PropertiesCapabilityPolicyConfigDefinitionRepository implements com.cloud.gateway.application.configuration.definition.CapabilityPolicyConfigDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<CapabilityPolicyConfig> source;
    public PropertiesCapabilityPolicyConfigDefinitionRepository(GatewayPolicyDefinitionProperties properties,
            StoredAccessSnapshotMapper mapper, CapabilityPolicyConfigPropertiesMapper policyConfiguration,
            StoredPolicyDefinitionCodec codec) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainPolicies(policyConfiguration.toStored(properties.getPolicies())),
                codec::encodeCapabilityPolicyConfig);
    }
    @Override public RevisionedAccessDefinition<CapabilityPolicyConfig> load() { return source.load(); }
}
