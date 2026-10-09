package com.cloud.gateway.application.configuration.definition;

import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import lombok.RequiredArgsConstructor;

/** Loads the same four typed definition documents for writable and read-only sources. */
@RequiredArgsConstructor
public final class AccessDefinitionReader {
    private final EndpointAccessDefinitionRepository endpoints;
    private final AppAccessDefinitionRepository apps;
    private final ClientAccessDefinitionRepository clients;
    private final CapabilityPolicyConfigDefinitionRepository policies;

    public ObservedAccessDefinitions load() {
        return new ObservedAccessDefinitions(endpoints.load(), apps.load(), clients.load(), policies.load());
    }

    public IngressContent loadContent() {
        return load().toContent();
    }
}
