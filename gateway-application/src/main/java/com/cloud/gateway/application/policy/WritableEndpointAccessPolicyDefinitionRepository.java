package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import java.util.Map;

public interface WritableEndpointAccessPolicyDefinitionRepository
        extends EndpointAccessPolicyDefinitionRepository,
        WritablePolicyDefinitionRepository<Map<EndpointId, EndpointPolicy>> {
}
