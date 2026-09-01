package com.cloud.gateway.application.policy;

import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class RouteAccessPolicyCommandService {
    private final WritableRouteAccessPolicyDefinitionRepository repository;
    private final PolicyRollbackState rollbackState;
    private final PolicyPublishLock publishLock;

    public void save(SaveRouteAccessPolicyCommand command) {
        publishLock.execute(() -> {
            verifyRollbackNotInProgress();
            VersionedPolicyDefinition<Map<EndpointId, IngressRouteAccessPolicy>> observed = repository.load();
            if (!command.expectedSourceRevision().equals(observed.sourceRevision())) {
                throw conflict();
            }
            Map<EndpointId, IngressRouteAccessPolicy> updated = new LinkedHashMap<>(observed.value());
            updated.put(command.policy().endpointId(), command.policy());
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
