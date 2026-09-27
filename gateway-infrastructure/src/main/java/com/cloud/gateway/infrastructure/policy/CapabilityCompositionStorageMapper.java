package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.capability.configuration.CapabilityComposition;

public interface CapabilityCompositionStorageMapper {
    StoredCapabilityComposition toStored(CapabilityComposition source);
    CapabilityComposition toDomain(StoredCapabilityComposition source);
}
