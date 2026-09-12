package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import java.util.Optional;

public interface PolicySnapshotHistory {
    Optional<IngressAccessSnapshot> find(long snapshotVersion);

    static PolicySnapshotHistory empty() {
        return snapshotVersion -> Optional.empty();
    }
}
