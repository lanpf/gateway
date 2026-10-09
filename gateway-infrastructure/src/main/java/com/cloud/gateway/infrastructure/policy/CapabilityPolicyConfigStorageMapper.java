package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;

public interface CapabilityPolicyConfigStorageMapper {
    StoredCapabilityPolicyConfig toStored(CapabilityPolicyConfig source);
    CapabilityPolicyConfig toDomain(StoredCapabilityPolicyConfig source);
}
