package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.policy.bundle.CapabilityComposition;

public interface CapabilityCompositionStorageMapper {
    StoredCapabilityComposition toStored(CapabilityComposition source);
    CapabilityComposition toDomain(StoredCapabilityComposition source);
}
