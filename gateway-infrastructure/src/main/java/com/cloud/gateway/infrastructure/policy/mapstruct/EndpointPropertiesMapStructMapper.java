package com.cloud.gateway.infrastructure.policy.mapstruct;

import com.cloud.gateway.infrastructure.policy.EndpointPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshot;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import java.util.List;
import com.cloud.framework.core.mapper.MapStructConfig;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public abstract class EndpointPropertiesMapStructMapper implements EndpointPropertiesMapper {
    @Override
    public abstract List<StoredAccessSnapshot.Endpoint> toStored(List<GatewayPolicyDefinitionProperties.Endpoint> source);
}
