package com.cloud.gateway.interfaces.security;

import com.cloud.gateway.api.constants.GatewayPathApiConstants;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.security.ManagementAccessRules;
import com.cloud.gateway.interfaces.webflux.GatewayRequestAttributes;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;

/** Classifies the local management protocol; its minimum access contract is a domain rule. */
@RequiredArgsConstructor
public final class ManagementEndpointContractValidator implements SmartInitializingSingleton {
    private static final String MANAGEMENT_DESCENDANT_PREFIX = GatewayPathApiConstants.ADMIN_BASE_PATH + "/";
    private final com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer provider;
    private final ManagementAccessRules rules;

    @Override
    public void afterSingletonsInstantiated() {
        IngressSnapshot snapshot = provider.capture().snapshot();
        for (Endpoint endpoint : snapshot.content().endpoints().asMap().values()) {
            if (GatewayRequestAttributes.LOCAL_ROUTE_ID.equals(endpoint.routeId())
                    || endpoint.matchers().stream().anyMatch(matcher ->
                            matcher.pathPattern().equals(GatewayPathApiConstants.ADMIN_BASE_PATH)
                                    || matcher.pathPattern().startsWith(MANAGEMENT_DESCENDANT_PREFIX))) {
                rules.validatePolicy(endpoint.access());
            }
        }
    }
}
