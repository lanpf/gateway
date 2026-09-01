package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.policy.IngressPolicySnapshot;
import java.util.Optional;

public interface PolicySnapshotHistory {
    Optional<IngressPolicySnapshot> find(long snapshotVersion);

    static PolicySnapshotHistory empty() {
        return snapshotVersion -> Optional.empty();
    }
}
