package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EndpointAccessPolicyCommandService {
    private final WritableEndpointAccessPolicyDefinitionRepository repository;
    private final PolicyDefinitionWriter writer;

    public EndpointAccessPolicyCommandService(WritableEndpointAccessPolicyDefinitionRepository repository,
            PolicyRollbackState rollbackState, PolicyPublishLock publishLock) {
        this.repository = repository;
        this.writer = new PolicyDefinitionWriter(rollbackState, publishLock);
    }

    public void save(SaveEndpointAccessPolicyCommand command) {
        writer.update(repository, command.expectedSourceRevision(), current -> {
            Map<EndpointId, EndpointPolicy> updated = new LinkedHashMap<>(current);
            updated.put(command.policy().endpointId(), command.policy());
            return Map.copyOf(updated);
        });
    }
}
