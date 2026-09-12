package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import java.util.Map;

public interface EndpointAccessPolicyDefinitionRepository
        extends PolicyDefinitionRepository<Map<EndpointId, EndpointPolicy>> {
}
