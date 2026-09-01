package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import java.util.Map;

public interface WritableRouteAccessPolicyDefinitionRepository
        extends RouteAccessPolicyDefinitionRepository,
        WritablePolicyDefinitionRepository<Map<EndpointId, IngressRouteAccessPolicy>> {
}
