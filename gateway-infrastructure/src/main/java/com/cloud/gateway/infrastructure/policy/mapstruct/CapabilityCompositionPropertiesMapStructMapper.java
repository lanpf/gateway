package com.cloud.gateway.infrastructure.policy.mapstruct;
import com.cloud.gateway.infrastructure.policy.*;
import com.cloud.gateway.infrastructure.policy.config.CapabilityCompositionProperties;
import org.mapstruct.Mapper;
@Mapper(config = PolicyStorageMapperConfig.class)
public abstract class CapabilityCompositionPropertiesMapStructMapper implements CapabilityCompositionPropertiesMapper {
    @Override public abstract StoredCapabilityComposition toStored(CapabilityCompositionProperties source);
}
