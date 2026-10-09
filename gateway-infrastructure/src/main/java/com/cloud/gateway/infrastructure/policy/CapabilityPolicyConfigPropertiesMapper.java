package com.cloud.gateway.infrastructure.policy;
import com.cloud.gateway.infrastructure.policy.config.CapabilityPolicyConfigProperties;
public interface CapabilityPolicyConfigPropertiesMapper {
    StoredCapabilityPolicyConfig toStored(CapabilityPolicyConfigProperties source);
}
