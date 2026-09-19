package com.cloud.gateway.application.configuration.publication;

import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class AccessPublicationCommandService {
    private final AccessDefinitions definitions;
    private final AccessSnapshotCapturer provider;
    private final AccessSnapshotPublication publication;
    private final AccessRollbackState rollbackState;
    private final AccessPublishLock publishLock;

    public IngressSnapshotUpdateOutput publish(PublishAccessSnapshotCommand command) {
        return publishLock.execute(() -> {
            if (rollbackState.isActive()) {
                throw new ApplicationException(ApplicationError.APP_POLICY_ROLLBACK_IN_PROGRESS);
            }
            com.cloud.gateway.application.configuration.definition.ObservedAccessDefinitions observed = definitions.load();
            RevisionedAccessSnapshot snapshot = provider.capture();
            IngressContent content = observed.toContent();
            IngressSnapshot candidate = publication.prepare(snapshot, content);
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
