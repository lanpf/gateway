package com.cloud.gateway.application.configuration.publication;

import com.cloud.gateway.application.configuration.definition.ObservedAccessDefinitions;
import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class AccessRollbackCommandService {
    private final AccessDefinitions definitions;
    private final AccessSnapshotCapturer provider;
    private final AccessSnapshotPublication publication;
    private final AccessSnapshotHistory history;
    private final AccessRollbackState rollbackState;
    private final AccessSnapshotHasher hasher;
    private final AccessPublishLock publishLock;
    private final AccessRollbackLock rollbackLock;

    public IngressSnapshotUpdateOutput rollback(RollbackAccessSnapshotCommand command) {
        return rollbackLock.execute(() -> publishLock.execute(() -> restore(command.targetSnapshotVersion())));
    }

    private IngressSnapshotUpdateOutput restore(SnapshotVersion targetVersion) {
        // Preflight neither creates a block nor clears a block left by a previous recovery.
        IngressSnapshot target = history.find(targetVersion).orElseThrow(
                () -> new ApplicationException(ApplicationError.APP_POLICY_SNAPSHOT_HISTORY_NOT_FOUND));
        ObservedAccessDefinitions observed = definitions.load();
        RevisionedAccessSnapshot snapshot = provider.capture();
        String targetHash = hasher.hash(target.content());
        boolean alreadyPublished = targetHash.equals(snapshot.snapshot().contentHash());
        IngressSnapshot candidate = null;
        if (alreadyPublished) {
            // Keep the original publication time: an expired client must not prevent
            // completing recovery after a successful publication whose block clear failed.
            publication.validateAlreadyPublished(snapshot, target.content());
        } else {
            // Any new publication is fully checked at one current time before recovery writes.
            candidate = publication.prepare(snapshot, target.content());
        }
        rollbackState.activate();
        // Each successful definition CAS is a checkpoint; failures deliberately retain the block.
        definitions.restore(observed, target.content());
        IngressContent restored = definitions.load().toContent();
        if (!targetHash.equals(hasher.hash(restored))) {
            throw new ApplicationException(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT);
        }
        IngressSnapshotUpdateOutput output = alreadyPublished
                ? new IngressSnapshotUpdateOutput(snapshot.snapshot().version(), targetHash,
                        snapshot.snapshot().publishedAt())
                : publication.publish(snapshot, candidate);
        rollbackState.clear();
        return output;
    }
}
