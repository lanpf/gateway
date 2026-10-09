package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.configuration.definition.WritableCapabilityPolicyConfigDefinitionRepository;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;

public final class NacosCapabilityPolicyConfigDefinitionRepository
        extends AbstractNacosAccessDefinitionRepository<CapabilityPolicyConfig>
        implements WritableCapabilityPolicyConfigDefinitionRepository {
    public NacosCapabilityPolicyConfigDefinitionRepository(NacosConfigOperations operations,
            NacosPolicyProperties properties, StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getPolicyConfigurationDefinitionDataId(),
                codec::decodeCapabilityPolicyConfig, codec::encodeCapabilityPolicyConfig);
    }
}
