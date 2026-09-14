package com.cloud.gateway.infrastructure.policy.lkg;

import java.util.Optional;

public interface LastKnownGoodAccessSnapshotStore {
    Optional<String> load();

    void save(String content);
}
