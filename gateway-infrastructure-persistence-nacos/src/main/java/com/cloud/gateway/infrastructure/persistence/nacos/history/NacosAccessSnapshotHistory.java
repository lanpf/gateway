package com.cloud.gateway.infrastructure.persistence.nacos.history;

import com.cloud.gateway.application.configuration.publication.AccessSnapshotHistory;
import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotCodec;

import java.util.Optional;

public final class NacosAccessSnapshotHistory implements AccessSnapshotHistory {
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
    public Optional<IngressSnapshot> find(SnapshotVersion snapshotVersion) {
        return source.findFirst(content -> matches(content, snapshotVersion)).map(this::decode);
    }

    private boolean matches(String content, SnapshotVersion snapshotVersion) {
        // SnapshotVersion is a record: compare by value, not by reference.
        return decode(content).version().equals(snapshotVersion);
    }

    /** History entries come from the same remote source as the live document, so they share its error. */
    private IngressSnapshot decode(String content) {
        try {
            return codec.decodeSnapshot(content);
        } catch (RuntimeException exception) {
            throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID, exception);
        }
    }
}
