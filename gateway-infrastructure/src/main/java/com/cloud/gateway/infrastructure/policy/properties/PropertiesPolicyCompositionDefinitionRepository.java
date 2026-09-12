package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.policy.PolicyCompositionDefinitionRepository;
import com.cloud.gateway.application.policy.VersionedPolicyDefinition;
import com.cloud.gateway.domain.policy.bundle.PolicyComposition;
import com.cloud.gateway.infrastructure.policy.PolicyCompositionPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;

public final class PropertiesPolicyCompositionDefinitionRepository implements PolicyCompositionDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<PolicyComposition> source;
    public PropertiesPolicyCompositionDefinitionRepository(GatewayPolicyDefinitionProperties properties,
            StoredPolicySnapshotMapper mapper, PolicyCompositionPropertiesMapper policyConfiguration,
            StoredPolicyDefinitionCodec codec) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainPolicies(policyConfiguration.toStored(properties.getPolicies())),
                codec::encodePolicyConfiguration);
    }
    @Override public VersionedPolicyDefinition<PolicyComposition> load() { return source.load(); }
}
