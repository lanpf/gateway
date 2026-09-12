package com.cloud.gateway.application.policy;

import lombok.RequiredArgsConstructor;

/** Management entry delegates to independent publication and recovery use cases. */
@RequiredArgsConstructor
public final class PolicySnapshotCommandService {
    private final PolicyPublicationCommandService publication;
    private final PolicyRollbackCommandService rollback;

    public PolicyPublicationOutput publish(PublishPolicySnapshotCommand command) {
        return publication.publish(command);
    }

    public PolicyPublicationOutput rollback(RollbackPolicySnapshotCommand command) {
        return rollback.rollback(command);
    }
}
