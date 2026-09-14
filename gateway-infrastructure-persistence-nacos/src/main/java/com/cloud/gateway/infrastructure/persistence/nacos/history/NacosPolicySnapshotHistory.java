package com.cloud.gateway.infrastructure.persistence.nacos.history;

import com.cloud.gateway.application.access.publication.AccessSnapshotHistory;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotCodec;

import java.util.Optional;

public final class NacosAccessSnapshotHistory implements com.cloud.gateway.application.access.publication.AccessSnapshotHistory {
    private final NacosPolicyHistorySource source;
    private final StoredAccessSnapshotCodec codec;

    public NacosAccessSnapshotHistory(
            NacosPolicyHistorySource source,
            StoredAccessSnapshotCodec codec
    ) {
        this.source = source;
        this.codec = codec;
    }

    @Override
    public Optional<IngressAccessSnapshot> find(long snapshotVersion) {
        return source.findFirst(content -> codec.decodeSnapshot(content).version() == snapshotVersion)
                .map(codec::decodeSnapshot);
    }
}
