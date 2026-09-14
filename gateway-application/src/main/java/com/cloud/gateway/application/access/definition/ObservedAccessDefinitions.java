package com.cloud.gateway.application.access.definition;

import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.policy.bundle.PolicyConfig;

import java.util.List;

/** One observation of the four independently versioned definition documents. */
public record ObservedAccessDefinitions(
        VersionedAccessDefinition<EndpointRegistry> endpoints,
        VersionedAccessDefinition<AppRegistry> apps,
        VersionedAccessDefinition<ClientRegistry> clients,
        VersionedAccessDefinition<PolicyConfig> policies
) {
    public IngressAccessContent toContent() {
        return new IngressAccessContent(apps.value(), clients.value(), endpoints.value(), policies.value());
    }

    public List<String> revisions() {
        return List.of(endpoints.sourceRevision(), apps.sourceRevision(), clients.sourceRevision(),
                policies.sourceRevision());
    }
}
