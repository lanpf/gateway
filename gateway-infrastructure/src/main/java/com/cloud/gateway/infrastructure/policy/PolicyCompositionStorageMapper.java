package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.policy.bundle.PolicyComposition;

public interface PolicyCompositionStorageMapper {
    StoredPolicyComposition toStored(PolicyComposition source);
    PolicyComposition toDomain(StoredPolicyComposition source);
}
