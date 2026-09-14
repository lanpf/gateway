package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.access.definition.WritableAppAccessDefinitionRepository;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import java.util.Map;

public final class NacosAppAccessDefinitionRepository
        extends AbstractNacosAccessDefinitionRepository<Map<AppId, AppAccess>>
        implements com.cloud.gateway.application.access.definition.WritableAppAccessDefinitionRepository {
    public NacosAppAccessDefinitionRepository(NacosConfigOperations operations, NacosPolicyProperties properties,
            StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getAppDefinitionDataId(),
                codec::decodeApps, codec::encodeApps);
    }
}
