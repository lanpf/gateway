package com.cloud.gateway.application.configuration.publication;

import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import java.time.Clock;
import lombok.RequiredArgsConstructor;

/** Single version-allocation and CAS boundary used by both use cases. */
@RequiredArgsConstructor
public final class AccessSnapshotPublication {
    private final AccessSnapshotPublisher publisher;
    private final AccessSnapshotAssembler assembler;
    private final Clock clock;

    IngressSnapshot prepare(RevisionedAccessSnapshot observed, IngressContent content) {
        return assembler.assembleUpdate(observed.snapshot(), content, clock.instant());
    }

    /** Recovery of already published content is not a new publication or client renewal. */
    void validateAlreadyPublished(RevisionedAccessSnapshot observed, IngressContent content) {
        assembler.assemble(observed.snapshot().version(), content, observed.snapshot().publishedAt());
    }

    IngressSnapshotUpdateOutput publish(RevisionedAccessSnapshot observed, IngressSnapshot next) {
        if (!publisher.compareAndSet(observed.sourceRevision(), next)) {
            throw new ApplicationException(ApplicationError.APP_POLICY_SNAPSHOT_PUBLISH_CONFLICT);
        }
        return new IngressSnapshotUpdateOutput(next.version(), next.contentHash(), next.publishedAt());
    }
}
