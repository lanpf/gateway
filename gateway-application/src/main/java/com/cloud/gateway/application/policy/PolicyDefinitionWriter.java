package com.cloud.gateway.application.policy;

import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;

/** Shared coordination only; each command owns its business transformation. */
@RequiredArgsConstructor
final class PolicyDefinitionWriter {
    private final PolicyRollbackState rollbackState;
    private final PolicyPublishLock publishLock;

    <T> void update(WritablePolicyDefinitionRepository<T> repository, String expectedRevision,
            UnaryOperator<T> change) {
        publishLock.execute(() -> {
            if (rollbackState.isActive()) {
                throw new ApplicationException(ApplicationError.APP_POLICY_ROLLBACK_IN_PROGRESS);
            }
            VersionedPolicyDefinition<T> observed = repository.load();
            if (!observed.sourceRevision().equals(expectedRevision)) {
                throw conflict();
            }
            T updated = change.apply(observed.value());
            if (!repository.compareAndSet(observed.sourceRevision(), updated)) {
                throw conflict();
            }
            return Boolean.TRUE;
        });
    }

    private static ApplicationException conflict() {
        return new ApplicationException(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT);
    }
}
