package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.policy.AuthorizationHeaderPolicy;
import com.cloud.gateway.domain.policy.ChannelCredentialRequirement;
import com.cloud.gateway.domain.policy.ChannelRequirement;
import com.cloud.gateway.domain.policy.ClientAppStatus;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.policy.SignaturePayloadContract;
import com.cloud.gateway.domain.policy.SubjectRequirement;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Map;

@JsonPropertyOrder({
        "channelAccessPolicies",
        "channelCredentialPolicies",
        "clientAppPolicies",
        "endpointCatalog",
        "routeAccessPolicies"
})
public record CanonicalPolicyContent(
        List<Endpoint> endpointCatalog,
        Map<String, ChannelAccess> channelAccessPolicies,
        Map<String, ChannelCredential> channelCredentialPolicies,
        Map<String, ClientApp> clientAppPolicies,
        Map<String, RouteAccess> routeAccessPolicies
) {
    public CanonicalPolicyContent {
        endpointCatalog = List.copyOf(endpointCatalog);
        channelAccessPolicies = Map.copyOf(channelAccessPolicies);
        channelCredentialPolicies = Map.copyOf(channelCredentialPolicies);
        clientAppPolicies = Map.copyOf(clientAppPolicies);
        routeAccessPolicies = Map.copyOf(routeAccessPolicies);
    }

    @JsonPropertyOrder({
            "deliveryRouteId",
            "endpointId",
            "matchers",
            "ownerService"
    })
    public record Endpoint(
            String deliveryRouteId,
            String endpointId,
            List<String> matchers,
            String ownerService
    ) {
        public Endpoint {
            matchers = List.copyOf(matchers);
        }
    }

    @JsonPropertyOrder({
            "allowedBrowserOrigins",
            "clientAppId",
            "status",
            "platform"
    })
    public record ClientApp(
            String clientAppId,
            ClientAppStatus status,
            String platform,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> allowedBrowserOrigins
    ) {
        public ClientApp {
            allowedBrowserOrigins = List.copyOf(allowedBrowserOrigins);
        }
    }

    @JsonPropertyOrder({
            "allowedClientAppIds",
            "channelCode"
    })
    public record ChannelAccess(List<String> allowedClientAppIds, String channelCode) {
        public ChannelAccess {
            allowedClientAppIds = List.copyOf(allowedClientAppIds);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({
            "activeCredential",
            "channelCode",
            "graceCredential"})
    public record ChannelCredential(
            Credential activeCredential,
            String channelCode,
            Credential graceCredential
    ) { }

    @JsonPropertyOrder({
            "channelCode",
            "credentialId",
            "expiresAt",
            "secretRef",
            "signaturePayloadContract",
            "validFrom"
    })
    public record Credential(
            String channelCode,
            String credentialId,
            String expiresAt,
            String secretRef,
            SignaturePayloadContract signaturePayloadContract,
            String validFrom
    ) { }

    @JsonPropertyOrder({
            "channelCredentialRequirement",
            "channelRequirement",
            "credentialForwardingPolicy",
            "enabled",
            "endpointId",
            "requiredAudiences",
            "requiredScopes",
            "subjectRequirement"
    })
    public record RouteAccess(
            ChannelCredentialRequirement channelCredentialRequirement,
            ChannelRequirement channelRequirement,
            Forwarding credentialForwardingPolicy,
            boolean enabled,
            String endpointId,
            List<String> requiredAudiences,
            List<String> requiredScopes,
            SubjectRequirement subjectRequirement
    ) {
        public RouteAccess {
            requiredAudiences = List.copyOf(requiredAudiences);
            requiredScopes = List.copyOf(requiredScopes);
        }
    }

    @JsonPropertyOrder({
            "allowedCookieNames",
            "authorizationHeader",
            "cookies"
    })
    public record Forwarding(
            List<String> allowedCookieNames,
            AuthorizationHeaderPolicy authorizationHeader,
            CookieForwardingMode cookies
    ) {
        public Forwarding {
            allowedCookieNames = List.copyOf(allowedCookieNames);
        }
    }
}
