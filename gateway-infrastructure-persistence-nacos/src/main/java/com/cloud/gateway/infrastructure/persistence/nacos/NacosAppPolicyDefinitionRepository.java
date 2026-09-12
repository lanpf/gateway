package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.policy.WritableAppPolicyDefinitionRepository;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import java.util.Map;

public final class NacosAppPolicyDefinitionRepository
        extends AbstractNacosPolicyDefinitionRepository<Map<AppId, AppPolicy>>
        implements WritableAppPolicyDefinitionRepository {
    public NacosAppPolicyDefinitionRepository(NacosConfigOperations operations, NacosPolicyProperties properties,
            StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getAppDefinitionDataId(),
                codec::decodeApps, codec::encodeApps);
    }
}
