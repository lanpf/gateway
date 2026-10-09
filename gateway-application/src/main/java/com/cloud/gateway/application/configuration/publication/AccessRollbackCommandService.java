package com.cloud.gateway.application.configuration.publication;

import com.cloud.gateway.application.configuration.definition.ObservedAccessDefinitions;
import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
    private final AccessOperationBudget budget;

    public IngressSnapshotUpdateOutput rollback(RollbackAccessSnapshotCommand command) {
        AccessOperationBudget.Deadline deadline = budget.start();
        long started = System.nanoTime();
        try {
            IngressSnapshot target = history.find(command.targetSnapshotVersion()).orElseThrow(
                    () -> new ApplicationException(ApplicationError.APP_POLICY_SNAPSHOT_HISTORY_NOT_FOUND));
            String targetHash = hasher.hash(target.content());
            deadline.check();
            IngressSnapshotUpdateOutput output = rollbackLock.execute(
                    () -> publishLock.execute(() -> restore(target, targetHash, deadline)));
            log.info("Access rollback completed targetVersion={} definitions={} durationMillis={}",
                    command.targetSnapshotVersion().value(), deadline.getCompletedDefinitions(),
                    (System.nanoTime() - started) / 1_000_000);
            return output;
        } catch (RuntimeException exception) {
            log.warn("Access rollback failed targetVersion={} definitions={} durationMillis={} exceptionType={}",
                    command.targetSnapshotVersion().value(), deadline.getCompletedDefinitions(),
                    (System.nanoTime() - started) / 1_000_000, exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private IngressSnapshotUpdateOutput restore(IngressSnapshot target, String targetHash,
                                                AccessOperationBudget.Deadline deadline) {
        deadline.check();
        // Preflight neither creates a block nor clears a block left by a previous recovery.
        ObservedAccessDefinitions observed = definitions.load(deadline);
        RevisionedAccessSnapshot snapshot = provider.capture();
        deadline.check();
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
        deadline.check();
        rollbackState.activate();
        // Each successful definition CAS is a checkpoint; failures deliberately retain the block.
        definitions.restore(observed, target.content(), deadline);
        IngressContent restored = definitions.load(deadline).toContent();
        deadline.check();
        if (!targetHash.equals(hasher.hash(restored))) {
            throw new ApplicationException(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT);
        }
        IngressSnapshotUpdateOutput output = alreadyPublished
                ? new IngressSnapshotUpdateOutput(snapshot.snapshot().version(), targetHash,
                        snapshot.snapshot().publishedAt())
                : publication.publish(snapshot, candidate);
        // If a write took the remaining budget, retain the durable block for reconciliation.
        deadline.check();
        rollbackState.clear();
        return output;
    }
}
