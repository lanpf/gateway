package com.cloud.gateway.infrastructure.policy;
import com.cloud.gateway.infrastructure.policy.config.PolicyConfigProperties;
public interface PolicyConfigPropertiesMapper {
    StoredPolicyConfig toStored(PolicyConfigProperties source);
}
