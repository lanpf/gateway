package com.cloud.gateway.infrastructure.policy.mapstruct;
import com.cloud.gateway.infrastructure.policy.*;
import com.cloud.gateway.infrastructure.policy.config.CapabilityCompositionProperties;
import com.cloud.framework.core.mapper.MapStructConfig;
import org.mapstruct.Mapper;
@Mapper(config = MapStructConfig.class, uses = CapabilityConfigCodecs.class,
        injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR)
public abstract class CapabilityCompositionPropertiesMapStructMapper implements CapabilityCompositionPropertiesMapper {
    @Override public abstract StoredCapabilityComposition toStored(CapabilityCompositionProperties source);
}
