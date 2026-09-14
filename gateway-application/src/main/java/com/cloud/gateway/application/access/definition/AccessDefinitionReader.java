package com.cloud.gateway.application.access.definition;

import com.cloud.gateway.domain.access.IngressAccessContent;
import lombok.RequiredArgsConstructor;

/** Loads the same five typed definition documents for writable and read-only sources. */
@RequiredArgsConstructor
public final class AccessDefinitionReader {
    private final EndpointAccessDefinitionRepository endpoints;
    private final AppAccessDefinitionRepository apps;
    private final ClientAccessDefinitionRepository clients;
    private final PolicyConfigDefinitionRepository policies;

    public ObservedAccessDefinitions load() {
        return new ObservedAccessDefinitions(endpoints.load(), apps.load(), clients.load(), policies.load());
    }

    public IngressAccessContent loadContent() {
        return load().toContent();
    }
}
