package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.policy.bundle.PolicyConfig;

public interface PolicyConfigStorageMapper {
    StoredPolicyConfig toStored(PolicyConfig source);
    PolicyConfig toDomain(StoredPolicyConfig source);
}
