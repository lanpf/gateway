package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import java.util.Map;

public interface ClientPropertiesMapper {
    Map<String, StoredPolicySnapshot.Client> toStored(Map<String, GatewayPolicyDefinitionProperties.Client> source);
}
