package com.cloud.gateway.infrastructure.policy;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;
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
        "apps",
        "endpoints",
        "policyConfig"
})
public record CanonicalIngressContent(
        List<Endpoint> endpoints,
        Map<String, Client> clients,
        Map<String, App> apps,
        StoredCapabilityPolicyConfig capabilities
) {
    public CanonicalIngressContent {
        Require.allNotNull(endpoints, clients, apps);
        endpoints = NullSafe.copyOf(endpoints);
        clients = NullSafe.copyOf(clients);
        apps = NullSafe.copyOf(apps);
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
            Require.allNotNull(tags, matchers);
            tags = NullSafe.copyOf(tags);
            matchers = NullSafe.copyOf(matchers);
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
            Require.allNotNull(requiredScopes, requiredAudiences);
            requiredScopes = NullSafe.copyOf(requiredScopes);
            requiredAudiences = NullSafe.copyOf(requiredAudiences);
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
            Require.notNull(allowedOrigins);
            allowedOrigins = NullSafe.copyOf(allowedOrigins);
        }
    }

    @JsonPropertyOrder({
            "allowedApps",
            "clientId",
            "status",
            "credentials"
    })
    public record Client(List<String> allowedAppIds, String clientId, ClientStatus status, ClientCredentials credentials) {
        public Client {
            Require.notNull(allowedAppIds);
            allowedAppIds = NullSafe.copyOf(allowedAppIds);
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
