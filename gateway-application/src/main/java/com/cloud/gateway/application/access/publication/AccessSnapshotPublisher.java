package com.cloud.gateway.application.access.publication;

import com.cloud.gateway.domain.access.IngressAccessSnapshot;

public interface AccessSnapshotPublisher {
    boolean compareAndSet(String expectedSourceRevision, IngressAccessSnapshot snapshot);
}
