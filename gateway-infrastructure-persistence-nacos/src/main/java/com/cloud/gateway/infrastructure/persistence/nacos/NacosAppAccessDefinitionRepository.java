package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.configuration.definition.WritableAppAccessDefinitionRepository;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;

public final class NacosAppAccessDefinitionRepository
        extends AbstractNacosAccessDefinitionRepository<AppRegistry>
        implements WritableAppAccessDefinitionRepository {
    public NacosAppAccessDefinitionRepository(NacosConfigOperations operations, NacosPolicyProperties properties,
            StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getAppDefinitionDataId(),
                codec::decodeApps, codec::encodeApps);
    }
}
