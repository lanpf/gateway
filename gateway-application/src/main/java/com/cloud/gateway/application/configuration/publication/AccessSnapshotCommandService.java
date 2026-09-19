package com.cloud.gateway.application.configuration.publication;

import lombok.RequiredArgsConstructor;

/** Management entry delegates to independent publication and recovery use cases. */
@RequiredArgsConstructor
public final class AccessSnapshotCommandService {
    private final AccessPublicationCommandService publication;
    private final AccessRollbackCommandService rollback;

    public IngressSnapshotUpdateOutput publish(PublishAccessSnapshotCommand command) {
        return publication.publish(command);
    }

    public IngressSnapshotUpdateOutput rollback(RollbackAccessSnapshotCommand command) {
        return rollback.rollback(command);
    }
}
