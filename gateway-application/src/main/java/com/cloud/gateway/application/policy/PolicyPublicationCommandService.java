package com.cloud.gateway.application.policy;

import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class PolicyPublicationCommandService {
    private final PolicyDefinitionAccess definitions;
    private final PolicySnapshotProvider provider;
    private final PolicySnapshotPublication publication;
    private final PolicyRollbackState rollbackState;
    private final PolicyPublishLock publishLock;

    public PolicyPublicationOutput publish(PublishPolicySnapshotCommand command) {
        return publishLock.execute(() -> {
            if (rollbackState.isActive()) {
                throw new ApplicationException(ApplicationError.APP_POLICY_ROLLBACK_IN_PROGRESS);
            }
            PolicyDefinitions observed = definitions.load();
            VersionedPolicySnapshot snapshot = provider.current();
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
