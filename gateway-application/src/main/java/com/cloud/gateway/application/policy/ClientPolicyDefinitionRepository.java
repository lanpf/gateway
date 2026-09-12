package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientPolicy;
import java.util.Map;

public interface ClientPolicyDefinitionRepository
        extends PolicyDefinitionRepository<Map<ClientId, ClientPolicy>> {
}
