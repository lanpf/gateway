package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.policy.ClientAppId;
import com.cloud.gateway.domain.policy.IngressClientAppPolicy;
import java.util.Map;

public interface ClientAppPolicyDefinitionRepository
        extends PolicyDefinitionRepository<Map<ClientAppId, IngressClientAppPolicy>> {
}
