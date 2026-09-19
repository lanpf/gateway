package com.cloud.gateway.application.configuration.editing;

import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.application.configuration.definition.WritableAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.publication.AccessPublishLock;
import com.cloud.gateway.application.configuration.publication.AccessRollbackState;
import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import java.util.function.UnaryOperator;

import com.cloud.gateway.domain.ingress.snapshot.Revision;
import lombok.RequiredArgsConstructor;

/** Shared coordination only; each command owns its business transformation. */
@RequiredArgsConstructor
final class AccessDefinitionWriter {
    private final AccessRollbackState rollbackState;
    private final AccessPublishLock publishLock;

    <T> Revision update(WritableAccessDefinitionRepository<T> repository,
                      Revision expectedRevision,
                      UnaryOperator<T> change) {
        return publishLock.execute(() -> {
            if (rollbackState.isActive()) {
                throw new ApplicationException(ApplicationError.APP_POLICY_ROLLBACK_IN_PROGRESS);
            }
            RevisionedAccessDefinition<T> observed = repository.load();
            if (!observed.sourceRevision().equals(expectedRevision)) {
                throw conflict();
            }
            T updated = change.apply(observed.value());
            Revision nextRevision = repository.compareAndSet(observed.sourceRevision(), updated);
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
