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
    private final AccessOperationBudget budget;

    public IngressSnapshotUpdateOutput publish(PublishAccessSnapshotCommand command) {
        AccessOperationBudget.Deadline deadline = budget.start();
        // Remote preflight stays outside the lock; revisions are revalidated before the CAS.
        com.cloud.gateway.application.configuration.definition.ObservedAccessDefinitions observed = definitions.load(deadline);
        deadline.check();
        return publishLock.execute(() -> {
            deadline.check();
            if (rollbackState.isActive()) {
                throw new ApplicationException(ApplicationError.APP_POLICY_ROLLBACK_IN_PROGRESS);
            }
            RevisionedAccessSnapshot snapshot = provider.capture();
            IngressContent content = observed.toContent();
            IngressSnapshot candidate = publication.prepare(snapshot, content);
            deadline.check();
            if (candidate.contentHash().equals(snapshot.snapshot().contentHash())) {
                throw new ApplicationException(ApplicationError.APP_POLICY_SNAPSHOT_NO_CHANGES);
            }
            if (!observed.revisions().equals(definitions.load(deadline).revisions())) {
                throw new ApplicationException(ApplicationError.APP_POLICY_DEFINITIONS_CHANGED);
            }
            deadline.check();
            IngressSnapshotUpdateOutput output = publication.publish(snapshot, candidate);
            deadline.check();
            return output;
        });
    }
}
