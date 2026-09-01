package com.cloud.gateway.domain.policy;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.EndpointCatalog;
import com.cloud.gateway.domain.endpoint.EndpointId;
import java.util.Map;

public record IngressPolicyContent(
        EndpointCatalog endpointCatalog,
        Map<ClientAppId, IngressClientAppPolicy> clientAppPolicies,
        Map<ChannelCode, IngressChannelAccessPolicy> channelAccessPolicies,
        Map<ChannelCode, IngressChannelCredentialPolicy> channelCredentialPolicies,
        Map<EndpointId, IngressRouteAccessPolicy> routeAccessPolicies
) {
    public IngressPolicyContent {
        Require.notNull(endpointCatalog, DomainException::missingField);
        clientAppPolicies = Map.copyOf(Require.notNull(clientAppPolicies, DomainException::missingField));
        channelAccessPolicies = Map.copyOf(Require.notNull(channelAccessPolicies, DomainException::missingField));
        channelCredentialPolicies = Map.copyOf(Require.notNull(
                channelCredentialPolicies, DomainException::missingField));
        routeAccessPolicies = Map.copyOf(Require.notNull(routeAccessPolicies, DomainException::missingField));
    }
}
