package com.cloud.gateway.application.configuration.publication;

import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.ingress.snapshot.Revision;

public interface AccessSnapshotPublisher {
    boolean compareAndSet(Revision expectedSourceRevision, IngressSnapshot snapshot);
}
