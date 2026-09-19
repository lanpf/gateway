package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import java.util.Map;

public interface AppPropertiesMapper {
    Map<String, StoredAccessSnapshot.App> toStored(Map<String, GatewayPolicyDefinitionProperties.App> source);
}
