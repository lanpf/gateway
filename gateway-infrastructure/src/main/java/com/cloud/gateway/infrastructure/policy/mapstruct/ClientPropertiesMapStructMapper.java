package com.cloud.gateway.infrastructure.policy.mapstruct;

import com.cloud.gateway.infrastructure.policy.ClientPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshot;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import java.util.Map;
import org.mapstruct.Mapper;

@Mapper(config = PolicyStorageMapperConfig.class)
public abstract class ClientPropertiesMapStructMapper implements ClientPropertiesMapper {
    @Override
    public abstract Map<String, StoredAccessSnapshot.Client> toStored(Map<String, GatewayPolicyDefinitionProperties.Client> source);
}
