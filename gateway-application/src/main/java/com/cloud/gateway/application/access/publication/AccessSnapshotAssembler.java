package com.cloud.gateway.application.access.publication;

import com.cloud.gateway.domain.access.AccessSnapshotVersion;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.policy.header.HeaderRules;
import java.time.Instant;
import lombok.RequiredArgsConstructor;

/** Shared full-snapshot construction for publication, recovery and initial local loading. */
@RequiredArgsConstructor
public final class AccessSnapshotAssembler {
    private final AccessSnapshotHasher hasher;
    private final HeaderRules headerRules;

    public IngressAccessSnapshot assemble(long version, IngressAccessContent content, Instant businessTime) {
        headerRules.validate(content.policies());
        return new IngressAccessSnapshot(new AccessSnapshotVersion(version), content, hasher.hash(content),
                businessTime);
    }

    public IngressAccessSnapshot assembleUpdate(IngressAccessSnapshot previous,
                                                IngressAccessContent content, Instant businessTime) {
        return assemble(Math.addExact(previous.version().value(), 1), content, businessTime);
    }
}
