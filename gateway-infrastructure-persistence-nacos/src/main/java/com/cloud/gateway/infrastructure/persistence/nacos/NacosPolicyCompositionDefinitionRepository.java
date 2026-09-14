package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.access.definition.WritablePolicyConfigDefinitionRepository;
import com.cloud.gateway.domain.policy.bundle.PolicyConfig;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;

import java.util.function.Function;

public final class NacosPolicyConfigDefinitionRepository
        extends AbstractNacosAccessDefinitionRepository<PolicyConfig>
        implements WritablePolicyConfigDefinitionRepository {
    public NacosPolicyConfigDefinitionRepository(NacosConfigOperations operations,
            NacosPolicyProperties properties, StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getPolicyConfigurationDefinitionDataId(),
                codec::decodePolicyConfiguration, codec::encodePolicyConfiguration);
    }
}
