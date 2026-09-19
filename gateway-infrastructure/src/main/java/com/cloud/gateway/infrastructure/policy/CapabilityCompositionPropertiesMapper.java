package com.cloud.gateway.infrastructure.policy;
import com.cloud.gateway.infrastructure.policy.config.CapabilityCompositionProperties;
public interface CapabilityCompositionPropertiesMapper {
    StoredCapabilityComposition toStored(CapabilityCompositionProperties source);
}
