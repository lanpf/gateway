package com.cloud.gateway.infrastructure.policy.mapstruct;

import com.cloud.gateway.infrastructure.policy.EndpointPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshot;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(config = PolicyStorageMapperConfig.class)
public abstract class EndpointPropertiesMapStructMapper implements EndpointPropertiesMapper {
    @Override
    public abstract List<StoredAccessSnapshot.Endpoint> toStored(List<GatewayPolicyDefinitionProperties.Endpoint> source);
}
