package com.cloud.gateway.application.policy;

import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.IngressChannelAccessPolicy;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ChannelAccessPolicyCommandService {
    private final WritableChannelAccessPolicyDefinitionRepository repository;
    private final PolicyRollbackState rollbackState;
    private final PolicyPublishLock publishLock;

    public void execute(SaveChannelAccessPolicyCommand command) {
        publishLock.execute(() -> {
            verifyRollbackNotInProgress();
            VersionedPolicyDefinition<Map<ChannelCode, IngressChannelAccessPolicy>> observed
                    = repository.load();
            if (!command.expectedSourceRevision().equals(observed.sourceRevision())) {
                throw conflict();
            }
            Map<ChannelCode, IngressChannelAccessPolicy> updated = new LinkedHashMap<>(observed.value());
            updated.put(command.policy().channelCode(), command.policy());
            if (!repository.compareAndSet(observed.sourceRevision(), Map.copyOf(updated))) {
                throw conflict();
            }
            return Boolean.TRUE;
        });
    }

    private void verifyRollbackNotInProgress() {
        if (rollbackState.isActive()) {
            throw new ApplicationException(ApplicationError.APP_POLICY_ROLLBACK_IN_PROGRESS);
        }
    }

    private static ApplicationException conflict() {
        return new ApplicationException(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT);
    }
}
