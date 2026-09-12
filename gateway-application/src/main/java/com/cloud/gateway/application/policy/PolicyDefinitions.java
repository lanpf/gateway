package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.policy.bundle.PolicyComposition;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientPolicy;
import java.util.List;
import java.util.Map;

/** One observation of the five independently versioned definition documents. */
record PolicyDefinitions(
        VersionedPolicyDefinition<EndpointRegistry> endpoints,
        VersionedPolicyDefinition<Map<AppId, AppPolicy>> apps,
        VersionedPolicyDefinition<Map<ClientId, ClientPolicy>> clients,
        VersionedPolicyDefinition<Map<EndpointId, EndpointPolicy>> endpointPolicies,
        VersionedPolicyDefinition<PolicyComposition> policies
) {
    IngressAccessContent toContent() {
        return new IngressAccessContent(endpoints.value(), apps.value(), clients.value(),
                endpointPolicies.value(), policies.value());
    }

    List<String> revisions() {
        return List.of(endpoints.sourceRevision(), apps.sourceRevision(), clients.sourceRevision(),
                endpointPolicies.sourceRevision(), policies.sourceRevision());
    }
}
