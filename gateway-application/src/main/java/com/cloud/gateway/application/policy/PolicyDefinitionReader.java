package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.access.IngressAccessContent;
import lombok.RequiredArgsConstructor;

/** Loads the same five typed definition documents for writable and read-only sources. */
@RequiredArgsConstructor
public final class PolicyDefinitionReader {
    private final EndpointRegistryDefinitionRepository endpoints;
    private final AppPolicyDefinitionRepository apps;
    private final ClientPolicyDefinitionRepository clients;
    private final EndpointAccessPolicyDefinitionRepository endpointPolicies;
    private final PolicyCompositionDefinitionRepository policies;

    PolicyDefinitions load() {
        return new PolicyDefinitions(endpoints.load(), apps.load(), clients.load(), endpointPolicies.load(), policies.load());
    }

    public IngressAccessContent loadContent() {
        return load().toContent();
    }
}
