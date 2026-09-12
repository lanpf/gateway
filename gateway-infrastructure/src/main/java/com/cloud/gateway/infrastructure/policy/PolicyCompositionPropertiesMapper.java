package com.cloud.gateway.infrastructure.policy;
import com.cloud.gateway.infrastructure.policy.config.PolicyCompositionProperties;
public interface PolicyCompositionPropertiesMapper {
    StoredPolicyComposition toStored(PolicyCompositionProperties source);
}
