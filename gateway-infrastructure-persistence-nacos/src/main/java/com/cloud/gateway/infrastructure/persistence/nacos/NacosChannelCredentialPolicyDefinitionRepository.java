package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.policy.WritableChannelCredentialPolicyDefinitionRepository;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.IngressChannelCredentialPolicy;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import java.util.Map;

public final class NacosChannelCredentialPolicyDefinitionRepository
        extends AbstractNacosPolicyDefinitionRepository<Map<ChannelCode, IngressChannelCredentialPolicy>>
        implements WritableChannelCredentialPolicyDefinitionRepository {
    public NacosChannelCredentialPolicyDefinitionRepository(NacosConfigOperations operations,
            NacosPolicyProperties properties, StoredPolicyDefinitionCodec codec) {
        super(operations, properties, properties.getChannelCredentialDefinitionDataId(),
                codec::decodeChannelCredentials, codec::encodeChannelCredentials);
    }
}
