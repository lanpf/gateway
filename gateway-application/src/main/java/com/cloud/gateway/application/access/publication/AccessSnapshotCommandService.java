package com.cloud.gateway.application.access.publication;

import lombok.RequiredArgsConstructor;

/** Management entry delegates to independent publication and recovery use cases. */
@RequiredArgsConstructor
public final class AccessSnapshotCommandService {
    private final AccessPublicationCommandService publication;
    private final AccessRollbackCommandService rollback;

    public AccessPublicationOutput publish(PublishAccessSnapshotCommand command) {
        return publication.publish(command);
    }

    public AccessPublicationOutput rollback(RollbackAccessSnapshotCommand command) {
        return rollback.rollback(command);
    }
}
