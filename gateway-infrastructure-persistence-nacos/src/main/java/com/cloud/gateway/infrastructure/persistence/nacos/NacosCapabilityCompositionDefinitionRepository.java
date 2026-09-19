package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.configuration.definition.WritableCapabilityCompositionDefinitionRepository;
import com.cloud.gateway.domain.policy.bundle.CapabilityComposition;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;

public final class NacosCapabilityCompositionDefinitionRepository
        extends AbstractNacosAccessDefinitionRepository<CapabilityComposition>
        implements WritableCapabilityCompositionDefinitionRepository {
    public NacosCapabilityCompositionDefinitionRepository(NacosConfigOperations operations,
            NacosPolicyProperties properties, StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getPolicyConfigurationDefinitionDataId(),
                codec::decodeCapabilityComposition, codec::encodeCapabilityComposition);
    }
}
