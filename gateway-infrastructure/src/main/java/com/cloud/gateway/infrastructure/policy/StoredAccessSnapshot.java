package com.cloud.gateway.infrastructure.policy;

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
import java.util.TreeSet;

public record StoredAccessSnapshot(
        long snapshotVersion,
        List<Endpoint> endpoints,
        Map<String, App> apps,
        Map<String, Client> clients,
        StoredCapabilityComposition policies,
        String contentHash,
        Instant publishedAt
) {
    public StoredAccessSnapshot {
        endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
        apps = apps == null ? Map.of() : Map.copyOf(apps);
        clients = clients == null ? Map.of() : Map.copyOf(clients);
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
            tags = tags == null ? Set.of() : Set.copyOf(new TreeSet<>(tags));
            matchers = matchers == null ? List.of() : List.copyOf(matchers);
        }
    }

    public record Access(
            ClientVerificationRequirement clientVerificationRequirement,
            SubjectAuthenticationRequirement subjectAuthenticationRequirement,
            Set<String> requiredScopes,
            Set<String> requiredAudiences
    ) {
        public Access {
            requiredScopes = requiredScopes == null ? Set.of() : Set.copyOf(new TreeSet<>(requiredScopes));
            requiredAudiences = requiredAudiences == null ? Set.of() : Set.copyOf(new TreeSet<>(requiredAudiences));
        }
    }

    public record Matcher(String httpMethod, String pathPattern) { }

    public record App(
            AppStatus status,
            String platform,
            Set<String> allowedOrigins
    ) {
        public App {
            allowedOrigins = allowedOrigins == null ? Set.of() : Set.copyOf(allowedOrigins);
        }
    }

    public record Client(Set<String> allowedAppIds, ClientStatus status, ClientCredentials credentials) {
        public Client {
            allowedAppIds = allowedAppIds == null ? Set.of() : Set.copyOf(allowedAppIds);
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
