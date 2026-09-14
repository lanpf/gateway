package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.policy.AuthorizationHeaderForwardingMode;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.client.ClientStatus;

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
        Map<String, Route> routes,
        StoredPolicyConfig policies,
        String contentHash,
        Instant publishedAt
) {
    public StoredAccessSnapshot {
        endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
        apps = apps == null ? Map.of() : Map.copyOf(apps);
        clients = clients == null ? Map.of() : Map.copyOf(clients);
        routes = routes == null ? Map.of() : Map.copyOf(routes);
    }

    public record Endpoint(String endpointId, String serviceId, String routeId, List<Matcher> matchers, Set<String> tags) {
        public Endpoint {
            tags = tags == null ? Set.of() : Set.copyOf(new TreeSet<>(tags));
            matchers = matchers == null ? List.of() : List.copyOf(matchers);
        }
    }

    public record Matcher(String httpMethod, String pathPattern) { }

    public record App(
            PolicyStatus status,
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

    public record Route(
            ClientVerificationMode clientVerificationMode,
            ClientVerificationRequirement clientVerificationRequirement,
            SubjectAuthenticationRequirement subjectAuthencationRequirement,
            Set<String> requiredScopes,
            Set<String> requiredAudiences,
            Forwarding credentialForwarding,
            EndpointStatus status
    ) {
        public Route {
            requiredScopes = requiredScopes == null ? Set.of() : Set.copyOf(requiredScopes);
            requiredAudiences = requiredAudiences == null ? Set.of() : Set.copyOf(requiredAudiences);
        }
    }

    public record Forwarding(
            AuthorizationHeaderForwardingMode authorizationHeader,
            CookieForwardingMode cookies,
            Set<String> allowedCookieNames
    ) {
        public Forwarding {
            allowedCookieNames = allowedCookieNames == null ? Set.of() : Set.copyOf(new TreeSet<>(allowedCookieNames));
        }
    }
}
