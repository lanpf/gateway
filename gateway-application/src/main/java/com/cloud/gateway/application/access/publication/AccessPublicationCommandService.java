package com.cloud.gateway.application.access.publication;

import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class AccessPublicationCommandService {
    private final AccessDefinitions definitions;
    private final AccessSnapshotProvider provider;
    private final AccessSnapshotPublication publication;
    private final AccessRollbackState rollbackState;
    private final AccessPublishLock publishLock;

    public AccessPublicationOutput publish(PublishAccessSnapshotCommand command) {
        return publishLock.execute(() -> {
            if (rollbackState.isActive()) {
                throw new ApplicationException(ApplicationError.APP_POLICY_ROLLBACK_IN_PROGRESS);
            }
            com.cloud.gateway.application.access.definition.ObservedAccessDefinitions observed = definitions.load();
            VersionedAccessSnapshot snapshot = provider.current();
            IngressAccessContent content = observed.toContent();
            IngressAccessSnapshot candidate = publication.prepare(snapshot, content);
            if (candidate.contentHash().equals(snapshot.snapshot().contentHash())) {
                throw new ApplicationException(ApplicationError.APP_POLICY_SNAPSHOT_NO_CHANGES);
            }
            if (!observed.revisions().equals(definitions.load().revisions())) {
                throw new ApplicationException(ApplicationError.APP_POLICY_DEFINITIONS_CHANGED);
            }
            return publication.publish(snapshot, candidate);
        });
    }
}
