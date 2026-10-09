package com.cloud.gateway.application.configuration.definition;

import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;

import java.util.List;

/** One observation of the four independently versioned definition documents. */
public record ObservedAccessDefinitions(
        RevisionedAccessDefinition<EndpointRegistry> endpoints,
        RevisionedAccessDefinition<AppRegistry> apps,
        RevisionedAccessDefinition<ClientRegistry> clients,
        RevisionedAccessDefinition<CapabilityPolicyConfig> policies
) {
    public IngressContent toContent() {
        return new IngressContent(apps.value(), clients.value(), endpoints.value(), policies.value());
    }

    public List<Revision> revisions() {
        return List.of(endpoints.sourceRevision(), apps.sourceRevision(), clients.sourceRevision(),
                policies.sourceRevision());
    }
}
