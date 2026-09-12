package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.access.IngressAccessSnapshot;

public interface PolicySnapshotPublisher {
    boolean compareAndSet(String expectedSourceRevision, IngressAccessSnapshot snapshot);
}
