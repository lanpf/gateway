package com.cloud.gateway.infrastructure.policy;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record StoredAccessSnapshot(
        long snapshotVersion,
        List<Endpoint> endpoints,
        Map<String, App> apps,
        Map<String, Client> clients,
        StoredCapabilityPolicyConfig policies,
        String contentHash,
        Instant publishedAt
) {
    public StoredAccessSnapshot {
        endpoints = NullSafe.copyOf(endpoints);
        apps = NullSafe.copyOf(apps, (k, v) -> Require.notBlank(k));
        clients = NullSafe.copyOf(clients, (k, v) -> Require.notBlank(k));
    }

    public record Endpoint(
            String endpointId,
            String serviceId,
            String routeId,
            List<Matcher> matchers,
            Set<String> tags,
            EndpointStatus status,
            Access access
    ) {
        public Endpoint {
            matchers = NullSafe.copyOf(matchers);
            tags = NullSafe.copyOf(tags, Require::notBlank);
        }
    }

    public record Access(
            ClientVerificationRequirement clientVerificationRequirement,
            SubjectAuthenticationRequirement subjectAuthenticationRequirement,
            Set<String> requiredScopes,
            Set<String> requiredAudiences
    ) {
        public Access {
            requiredScopes = NullSafe.copyOf(requiredScopes, Require::notBlank);
            requiredAudiences = NullSafe.copyOf(requiredAudiences, Require::notBlank);
        }
    }

    public record Matcher(String httpMethod, String pathPattern) { }

    public record App(
            AppStatus status,
            String platform,
            Set<String> allowedOrigins
    ) {
        public App {
            allowedOrigins = NullSafe.copyOf(allowedOrigins, Require::notBlank);
        }
    }

    public record Client(Set<String> allowedAppIds, ClientStatus status, ClientCredentials credentials) {
        public Client {
            allowedAppIds = NullSafe.copyOf(allowedAppIds, Require::notBlank);
        }
    }

    public record ClientCredentials(Credential activeCredential, Credential graceCredential) { }

    public record Credential(
            String credentialId,
            Instant validFrom,
            Instant expiresAt,
            SignatureContract signatureContract,
            String secretRef
    ) { }
}
