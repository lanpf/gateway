package com.cloud.gateway.domain.ingress.snapshot;


import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;

public record IngressContent(
        AppRegistry apps,
        ClientRegistry clients,
        EndpointRegistry endpoints,
        CapabilityPolicyConfig policyConfig
) {
    public IngressContent {
        Require.allNotNull(apps, clients, endpoints, policyConfig);
    }
}
