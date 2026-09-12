package com.cloud.gateway.application.policy;

import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class PolicyRollbackCommandService {
    private final PolicyDefinitionAccess definitions;
    private final PolicySnapshotProvider provider;
    private final PolicySnapshotPublication publication;
    private final PolicySnapshotHistory history;
    private final PolicyRollbackState rollbackState;
    private final PolicySnapshotHasher hasher;
    private final PolicyPublishLock publishLock;
    private final PolicyRollbackLock rollbackLock;

    public PolicyPublicationOutput rollback(RollbackPolicySnapshotCommand command) {
        return rollbackLock.execute(() -> publishLock.execute(() -> restore(command.targetSnapshotVersion())));
    }

    private PolicyPublicationOutput restore(long targetVersion) {
        // Preflight neither creates a block nor clears a block left by a previous recovery.
        IngressAccessSnapshot target = history.find(targetVersion).orElseThrow(
                () -> new ApplicationException(ApplicationError.APP_POLICY_SNAPSHOT_HISTORY_NOT_FOUND));
        PolicyDefinitions observed = definitions.load();
        VersionedPolicySnapshot snapshot = provider.current();
        target.content().validateAppUpdatesFrom(observed.toContent());
        String targetHash = hasher.hash(target.content());
        boolean alreadyPublished = targetHash.equals(snapshot.snapshot().contentHash());
        IngressAccessSnapshot candidate = null;
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
        IngressAccessContent restored = definitions.load().toContent();
        if (!targetHash.equals(hasher.hash(restored))) {
            throw new ApplicationException(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT);
        }
        PolicyPublicationOutput output = alreadyPublished
                ? new PolicyPublicationOutput(snapshot.snapshot().version(), targetHash)
                : publication.publish(snapshot, candidate);
        rollbackState.clear();
        return output;
    }
}
