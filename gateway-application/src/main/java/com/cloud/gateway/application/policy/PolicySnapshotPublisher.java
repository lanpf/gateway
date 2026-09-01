package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.policy.IngressPolicySnapshot;

public interface PolicySnapshotPublisher {
    boolean compareAndSet(String expectedSourceRevision, IngressPolicySnapshot snapshot);
}
