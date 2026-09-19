package com.cloud.gateway.application.configuration.publication;

import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import java.util.Optional;

public interface AccessSnapshotHistory {
    Optional<IngressSnapshot> find(SnapshotVersion snapshotVersion);

    static AccessSnapshotHistory empty() {
        return snapshotVersion -> Optional.empty();
    }
}
