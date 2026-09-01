package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.IngressChannelCredentialPolicy;
import java.util.Map;

public interface ChannelCredentialPolicyDefinitionRepository
        extends PolicyDefinitionRepository<Map<ChannelCode, IngressChannelCredentialPolicy>> {
}
