package com.cloud.gateway.application.access.editing;

import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;

/** Shared coordination only; each command owns its business transformation. */
@RequiredArgsConstructor
final class AccessDefinitionWriter {
    private final com.cloud.gateway.application.access.publication.AccessRollbackState rollbackState;
    private final com.cloud.gateway.application.access.publication.AccessPublishLock publishLock;

    <T> String update(com.cloud.gateway.application.access.definition.WritableAccessDefinitionRepository<T> repository, String expectedRevision,
            UnaryOperator<T> change) {
        return publishLock.execute(() -> {
            if (rollbackState.isActive()) {
                throw new ApplicationException(ApplicationError.APP_POLICY_ROLLBACK_IN_PROGRESS);
            }
            com.cloud.gateway.application.access.definition.VersionedAccessDefinition<T> observed = repository.load();
            if (!observed.sourceRevision().equals(expectedRevision)) {
                throw conflict();
            }
            T updated = change.apply(observed.value());
            String nextRevision = repository.compareAndSet(observed.sourceRevision(), updated);
            if (nextRevision == null) {
                throw conflict();
            }
            return nextRevision;
        });
    }

    private static ApplicationException conflict() {
        return new ApplicationException(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT);
    }
}
