package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.policy.AuthorizationHeaderForwardingMode;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Map;

@JsonPropertyOrder({
        "clients",
        "appPolicies",
        "endpointRegistry",
        "endpointPolicies",
        "policies"
})
public record CanonicalPolicyContent(
        List<Endpoint> endpointRegistry,
        Map<String, Client> clientPolicies,
        Map<String, App> appPolicies,
        Map<String, EndpointAccess> endpointPolicies,
        StoredPolicyConfig policies
) {
    public CanonicalPolicyContent {
        endpointRegistry = List.copyOf(endpointRegistry);
        clientPolicies = Map.copyOf(clientPolicies);
        appPolicies = Map.copyOf(appPolicies);
        endpointPolicies = Map.copyOf(endpointPolicies);
    }

    @JsonPropertyOrder({
            "routeId",
            "id",
            "matchers",
            "serviceId",
            "tags"
    })
    public record Endpoint(
            String routeId,
            String endpointId,
            List<String> matchers,
            String serviceId,
            List<String> tags
    ) {
        public Endpoint {
            tags = List.copyOf(tags);
            matchers = List.copyOf(matchers);
        }
    }

    @JsonPropertyOrder({
            "allowedOrigins",
            "appId",
            "status",
            "platform"
    })
    public record App(
            String appId,
            PolicyStatus status,
            String platform,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> allowedOrigins
    ) {
        public App {
            allowedOrigins = List.copyOf(allowedOrigins);
        }
    }

    @JsonPropertyOrder({
            "allowedApps",
            "clientId",
            "status", "credentials"
    })
    public record Client(List<String> allowedAppIds, String clientId, ClientStatus status, ClientCredentials credentials) {
        public Client {
            allowedAppIds = List.copyOf(allowedAppIds);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({
            "activeCredential",
            "graceCredential"})
    public record ClientCredentials(
            Credential activeCredential,
            Credential graceCredential
    ) { }

    @JsonPropertyOrder({
            "clientId",
            "credentialId",
            "expiresAt",
            "secretRef",
            "signatureContract",
            "validFrom"
    })
    public record Credential(
            String clientId,
            String credentialId,
            String expiresAt,
            String secretRef,
            SignatureContract signatureContract,
            String validFrom
    ) { }

    @JsonPropertyOrder({
            "clientCredentialRequirement",
            "clientRequirement",
            "credentialForwarding",
            "status",
            "id",
            "requiredAudiences",
            "requiredScopes",
            "subjectRequirement"
    })
    public record EndpointAccess(
            ClientVerificationMode clientVerificationMode,
            ClientVerificationRequirement clientVerificationRequirement,
            Forwarding credentialForwarding,
            EndpointStatus status,
            String endpointId,
            List<String> requiredAudiences,
            List<String> requiredScopes,
            SubjectAuthenticationRequirement subjectAuthencationRequirement
    ) {
        public EndpointAccess {
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
            AuthorizationHeaderForwardingMode authorizationHeader,
            CookieForwardingMode cookies
    ) {
        public Forwarding {
            allowedCookieNames = List.copyOf(allowedCookieNames);
        }
    }
}
