package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.IngressChannelAccessPolicy;
import java.util.Map;

public interface WritableChannelAccessPolicyDefinitionRepository
        extends ChannelAccessPolicyDefinitionRepository,
        WritablePolicyDefinitionRepository<Map<ChannelCode, IngressChannelAccessPolicy>> {
}
