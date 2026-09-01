package com.cloud.gateway.infrastructure.persistence.nacos.history;

import com.cloud.gateway.application.policy.PolicySnapshotHistory;
import com.cloud.gateway.domain.policy.IngressPolicySnapshot;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotCodec;

import java.util.Optional;

public final class NacosPolicySnapshotHistory implements PolicySnapshotHistory {
    private final NacosPolicyHistorySource source;
    private final StoredPolicySnapshotCodec codec;

    public NacosPolicySnapshotHistory(
            NacosPolicyHistorySource source,
            StoredPolicySnapshotCodec codec
    ) {
        this.source = source;
        this.codec = codec;
    }

    @Override
    public Optional<IngressPolicySnapshot> find(long snapshotVersion) {
        return source.findFirst(content -> codec.decodeSnapshot(content).snapshotVersion() == snapshotVersion)
                .map(codec::decodeSnapshot);
    }
}
