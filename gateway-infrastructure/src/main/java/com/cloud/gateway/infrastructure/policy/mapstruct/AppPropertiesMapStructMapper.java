package com.cloud.gateway.infrastructure.policy.mapstruct;

import com.cloud.gateway.infrastructure.policy.AppPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshot;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import java.util.Map;
import org.mapstruct.Mapper;

@Mapper(config = PolicyStorageMapperConfig.class)
public abstract class AppPropertiesMapStructMapper implements AppPropertiesMapper {
    @Override
    public abstract Map<String, StoredAccessSnapshot.App> toStored(Map<String, GatewayPolicyDefinitionProperties.App> source);
}
