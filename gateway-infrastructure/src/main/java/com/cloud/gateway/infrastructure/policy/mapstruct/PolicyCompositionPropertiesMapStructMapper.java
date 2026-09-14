package com.cloud.gateway.infrastructure.policy.mapstruct;
import com.cloud.gateway.infrastructure.policy.*;
import com.cloud.gateway.infrastructure.policy.config.PolicyConfigProperties;
import org.mapstruct.Mapper;
@Mapper(config = PolicyStorageMapperConfig.class)
public interface PolicyConfigPropertiesMapStructMapper extends PolicyConfigPropertiesMapper {
    @Override StoredPolicyConfig toStored(PolicyConfigProperties source);
}
