package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.policy.WritableChannelAccessPolicyDefinitionRepository;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.IngressChannelAccessPolicy;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import java.util.Map;

public final class NacosChannelAccessPolicyDefinitionRepository
        extends AbstractNacosPolicyDefinitionRepository<Map<ChannelCode, IngressChannelAccessPolicy>>
        implements WritableChannelAccessPolicyDefinitionRepository {
    public NacosChannelAccessPolicyDefinitionRepository(NacosConfigOperations operations,
            NacosPolicyProperties properties, StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getChannelAccessDefinitionDataId(),
                codec::decodeChannelAccess, codec::encodeChannelAccess);
    }
}
