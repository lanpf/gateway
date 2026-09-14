package com.cloud.gateway.application.access.publication;

import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import java.time.Clock;
import lombok.RequiredArgsConstructor;

/** Single version-allocation and CAS boundary used by both use cases. */
@RequiredArgsConstructor
public final class AccessSnapshotPublication {
    private final AccessSnapshotPublisher publisher;
    private final AccessSnapshotAssembler assembler;
    private final Clock clock;

    IngressAccessSnapshot prepare(VersionedAccessSnapshot observed, IngressAccessContent content) {
        return assembler.assembleUpdate(observed.snapshot(), content, clock.instant());
    }

    /** Recovery of already published content is not a new publication or client renewal. */
    void validateAlreadyPublished(VersionedAccessSnapshot observed, IngressAccessContent content) {
        assembler.assemble(observed.snapshot().version().value(), content, observed.snapshot().publishedAt());
    }

    AccessPublicationOutput publish(VersionedAccessSnapshot observed, IngressAccessSnapshot next) {
        if (!publisher.compareAndSet(observed.sourceRevision(), next)) {
            throw new ApplicationException(ApplicationError.APP_POLICY_SNAPSHOT_PUBLISH_CONFLICT);
        }
        return new AccessPublicationOutput(next.version().value(), next.contentHash(), next.publishedAt());
    }
}
