package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.access.definition.PolicyConfigDefinitionRepository;
import com.cloud.gateway.application.access.definition.VersionedAccessDefinition;
import com.cloud.gateway.domain.policy.bundle.PolicyConfig;
import com.cloud.gateway.infrastructure.policy.PolicyConfigPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;

public final class PropertiesPolicyConfigDefinitionRepository implements com.cloud.gateway.application.access.definition.PolicyConfigDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<PolicyConfig> source;
    public PropertiesPolicyConfigDefinitionRepository(GatewayPolicyDefinitionProperties properties,
            StoredAccessSnapshotMapper mapper, PolicyConfigPropertiesMapper policyConfiguration,
            StoredPolicyDefinitionCodec codec) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainPolicies(policyConfiguration.toStored(properties.getPolicies())),
                codec::encodePolicyConfiguration);
    }
    @Override public com.cloud.gateway.application.access.definition.VersionedAccessDefinition<PolicyConfig> load() { return source.load(); }
}
