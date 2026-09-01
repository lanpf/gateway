package com.cloud.gateway.application.policy;

import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.domain.policy.ClientAppId;
import com.cloud.gateway.domain.policy.IngressClientAppPolicy;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ClientAppPolicyCommandService {
    private final WritableClientAppPolicyDefinitionRepository repository;
    private final PolicyRollbackState rollbackState;
    private final PolicyPublishLock publishLock;

    public void save(SaveClientAppPolicyCommand command) {
        publishLock.execute(() -> {
            verifyRollbackNotInProgress();
            VersionedPolicyDefinition<Map<ClientAppId, IngressClientAppPolicy>> observed = repository.load();
            verifyRevision(command.expectedSourceRevision(), observed.sourceRevision());
            Map<ClientAppId, IngressClientAppPolicy> updated = new LinkedHashMap<>(observed.value());
            updated.put(command.policy().clientAppId(), command.policy());
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

    private static void verifyRevision(String expected, String observed) {
        if (!expected.equals(observed)) {
            throw conflict();
        }
    }

    private static ApplicationException conflict() {
        return new ApplicationException(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT);
    }
}
