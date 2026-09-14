package com.cloud.gateway.application.access.definition;

import com.cloud.gateway.domain.client.ClientRegistry;

public interface WritableClientAccessDefinitionRepository
        extends ClientAccessDefinitionRepository,
        WritableAccessDefinitionRepository<ClientRegistry> {
}
