package com.cloud.gateway.application.policy;

import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import java.time.Clock;
import lombok.RequiredArgsConstructor;

/** Single version-allocation and CAS boundary used by both use cases. */
@RequiredArgsConstructor
public final class PolicySnapshotPublication {
    private final PolicySnapshotPublisher publisher;
    private final PolicySnapshotAssembler assembler;
    private final Clock clock;

    IngressAccessSnapshot prepare(VersionedPolicySnapshot observed, IngressAccessContent content) {
        return assembler.assembleUpdate(observed.snapshot(), content, clock.instant());
    }

    /** Recovery of already published content is not a new publication or client renewal. */
    void validateAlreadyPublished(VersionedPolicySnapshot observed, IngressAccessContent content) {
        content.validateAppUpdatesFrom(observed.snapshot().content());
        assembler.assemble(observed.snapshot().version(), content, observed.snapshot().publishedAt());
    }

    PolicyPublicationOutput publish(VersionedPolicySnapshot observed, IngressAccessSnapshot next) {
        if (!publisher.compareAndSet(observed.sourceRevision(), next)) {
            throw new ApplicationException(ApplicationError.APP_POLICY_SNAPSHOT_PUBLISH_CONFLICT);
        }
        return new PolicyPublicationOutput(next.version(), next.contentHash());
    }
}
