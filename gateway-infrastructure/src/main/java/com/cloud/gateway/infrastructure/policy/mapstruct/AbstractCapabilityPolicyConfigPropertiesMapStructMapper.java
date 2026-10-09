package com.cloud.gateway.infrastructure.policy.mapstruct;
import com.cloud.gateway.infrastructure.policy.*;
import com.cloud.gateway.infrastructure.policy.config.CapabilityPolicyConfigProperties;
import com.cloud.framework.core.mapper.MapStructConfig;
import org.mapstruct.Mapper;
@Mapper(config = MapStructConfig.class, uses = CapabilityConfigCodecs.class,
        injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR)
public abstract class AbstractCapabilityPolicyConfigPropertiesMapStructMapper implements CapabilityPolicyConfigPropertiesMapper {
    @Override public abstract StoredCapabilityPolicyConfig toStored(CapabilityPolicyConfigProperties source);
}
