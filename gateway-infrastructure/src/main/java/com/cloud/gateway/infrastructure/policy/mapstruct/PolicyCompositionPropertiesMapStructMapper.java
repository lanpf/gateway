package com.cloud.gateway.infrastructure.policy.mapstruct;
import com.cloud.gateway.infrastructure.policy.*;
import com.cloud.gateway.infrastructure.policy.config.PolicyCompositionProperties;
import org.mapstruct.Mapper;
@Mapper(config = PolicyStorageMapperConfig.class)
public interface PolicyCompositionPropertiesMapStructMapper extends PolicyCompositionPropertiesMapper {
    @Override StoredPolicyComposition toStored(PolicyCompositionProperties source);
}
