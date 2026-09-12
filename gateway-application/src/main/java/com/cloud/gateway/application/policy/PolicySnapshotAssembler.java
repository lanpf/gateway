package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.policy.header.HeaderRules;
import java.time.Instant;
import lombok.RequiredArgsConstructor;

/** Shared full-snapshot construction for publication, recovery and initial local loading. */
@RequiredArgsConstructor
public final class PolicySnapshotAssembler {
    private final PolicySnapshotHasher hasher;
    private final HeaderRules headerRules;

    public IngressAccessSnapshot assemble(long version, IngressAccessContent content, Instant businessTime) {
        headerRules.validate(content.policies());
        return new IngressAccessSnapshot(version, content, hasher.hash(content), businessTime);
    }

    public IngressAccessSnapshot assembleUpdate(IngressAccessSnapshot previous,
                                                IngressAccessContent content, Instant businessTime) {
        content.validateAppUpdatesFrom(previous.content());
        return assemble(Math.addExact(previous.version(), 1), content, businessTime);
    }
}
