package com.cloud.gateway.application.access.editing;

import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.validation.Require;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class EndpointAccessCommandService {
    private final com.cloud.gateway.application.access.definition.WritableEndpointAccessDefinitionRepository repository;
    private final AccessDefinitionWriter writer;

    public EndpointAccessCommandService(com.cloud.gateway.application.access.definition.WritableEndpointAccessDefinitionRepository repository,
                                        com.cloud.gateway.application.access.publication.AccessRollbackState rollbackState, com.cloud.gateway.application.access.publication.AccessPublishLock publishLock) {
        this.repository = repository;
        this.writer = new AccessDefinitionWriter(rollbackState, publishLock);
    }

    /** Content-only upsert: creation initializes status to ENABLED, updates preserve the existing status. */
    public String save(SaveEndpointAccessCommand command) {
        return writer.update(repository, command.expectedSourceRevision(), current -> {
            Endpoint existing = current.find(command.endpointId()).orElse(null);
            Endpoint next = existing == null
                    ? new Endpoint(command.endpointId(), command.serviceId(), command.routeId(),
                            command.matchers(), command.tags(), EndpointStatus.ENABLED, command.policy())
                    : new Endpoint(command.endpointId(), command.serviceId(), command.routeId(),
                            command.matchers(), command.tags(), existing.status(), command.policy());
            return withEndpoint(current, next);
        });
    }

    /** Dedicated enable/disable transition; the only command that changes an endpoint's status. */
    public String setEndpointStatus(SetEndpointStatusCommand command) {
        return writer.update(repository, command.expectedSourceRevision(), current -> {
            Endpoint existing = Require.that(current.find(command.endpointId()).orElse(null), Objects::nonNull,
                    () -> new DomainException(DomainError.ENDPOINT_NOT_FOUND));
            return withEndpoint(current, new Endpoint(existing.endpointId(), existing.serviceId(),
                    existing.routeId(), existing.matchers(), existing.tags(), command.status(),
                    existing.access()));
        });
    }

    private static EndpointRegistry withEndpoint(EndpointRegistry current, Endpoint next) {
        Map<EndpointId, Endpoint> updated = new LinkedHashMap<>(current.asMap());
        updated.put(next.endpointId(), next);
        return new EndpointRegistry(updated.values());
    }
}
