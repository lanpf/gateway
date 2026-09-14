package com.cloud.gateway.infrastructure.policy.mapstruct;

import com.cloud.gateway.infrastructure.policy.ClientPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshot;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import java.util.Map;
import org.mapstruct.Mapper;

@Mapper(config = PolicyStorageMapperConfig.class)
public interface ClientPropertiesMapStructMapper extends ClientPropertiesMapper {
    @Override
    Map<String, StoredAccessSnapshot.Client> toStored(Map<String, GatewayPolicyDefinitionProperties.Client> source);
}
