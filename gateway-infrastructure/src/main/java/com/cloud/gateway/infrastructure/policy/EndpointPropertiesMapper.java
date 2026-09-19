package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import java.util.List;

public interface EndpointPropertiesMapper {
    List<StoredAccessSnapshot.Endpoint> toStored(List<GatewayPolicyDefinitionProperties.Endpoint> source);
}
