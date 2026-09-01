package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.policy.IngressPolicyContent;

public interface PolicySnapshotHasher {
    String hash(IngressPolicyContent content);
}
