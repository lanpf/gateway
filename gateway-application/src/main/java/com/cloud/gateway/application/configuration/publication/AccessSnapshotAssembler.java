package com.cloud.gateway.application.configuration.publication;

import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.policy.header.HeaderRules;
import java.time.Instant;
import lombok.RequiredArgsConstructor;

/** Shared full-snapshot construction for publication, recovery and initial local loading. */
@RequiredArgsConstructor
public final class AccessSnapshotAssembler {
    private final AccessSnapshotHasher hasher;
    private final HeaderRules headerRules;

    /** Assembles at an explicit version; recovery re-uses an already published version. */
    public IngressSnapshot assemble(SnapshotVersion version, IngressContent content,
                                          Instant businessTime) {
        content.capabilityComposition().validate(content);
        headerRules.validate(content.capabilityComposition());
        return new IngressSnapshot(version, content, hasher.hash(content), businessTime);
    }

    /** Publication always advances the version, so the caller never computes one. */
    public IngressSnapshot assembleUpdate(IngressSnapshot previous,
                                                IngressContent content, Instant businessTime) {
        return assemble(new SnapshotVersion(Math.addExact(previous.version().value(), 1)), content, businessTime);
    }
}
