package com.cloud.gateway.application.access.publication;

import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import java.util.Optional;

public interface AccessSnapshotHistory {
    Optional<IngressAccessSnapshot> find(long snapshotVersion);

    static AccessSnapshotHistory empty() {
        return snapshotVersion -> Optional.empty();
    }
}
