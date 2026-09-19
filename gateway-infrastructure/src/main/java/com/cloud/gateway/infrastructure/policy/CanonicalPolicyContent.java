package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.client.ClientStatus;
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
        "capabilityComposition"
})
public record CanonicalPolicyContent(
        List<Endpoint> endpointRegistry,
        Map<String, Client> clientPolicies,
        Map<String, App> appPolicies,
        StoredCapabilityComposition policies
) {
    public CanonicalPolicyContent {
        endpointRegistry = List.copyOf(endpointRegistry);
        clientPolicies = Map.copyOf(clientPolicies);
        appPolicies = Map.copyOf(appPolicies);
    }

    @JsonPropertyOrder({
            "routeId",
            "id",
            "matchers",
            "serviceId",
            "status",
            "tags",
            "access"
    })
    public record Endpoint(
            String routeId,
            String endpointId,
            List<String> matchers,
            String serviceId,
            List<String> tags,
            EndpointStatus status,
            EndpointAccess access
    ) {
        public Endpoint {
            tags = List.copyOf(tags);
            matchers = List.copyOf(matchers);
        }
    }

    @JsonPropertyOrder({
            "clientVerificationRequirement",
            "requiredAudiences",
            "requiredScopes",
            "subjectAuthenticationRequirement"
    })
    public record EndpointAccess(
            ClientVerificationRequirement clientVerificationRequirement,
            SubjectAuthenticationRequirement subjectAuthenticationRequirement,
            List<String> requiredScopes,
            List<String> requiredAudiences
    ) {
        public EndpointAccess {
            requiredScopes = List.copyOf(requiredScopes);
            requiredAudiences = List.copyOf(requiredAudiences);
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
            AppStatus status,
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
}
