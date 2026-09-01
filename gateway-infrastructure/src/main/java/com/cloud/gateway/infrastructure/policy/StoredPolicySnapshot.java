package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.policy.AuthorizationHeaderPolicy;
import com.cloud.gateway.domain.policy.ChannelCredentialRequirement;
import com.cloud.gateway.domain.policy.ChannelRequirement;
import com.cloud.gateway.domain.policy.ClientAppStatus;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.policy.SignaturePayloadContract;
import com.cloud.gateway.domain.policy.SubjectRequirement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record StoredPolicySnapshot(
        long snapshotVersion,
        List<Endpoint> endpoints,
        Map<String, ClientApp> clientApps,
        Map<String, ChannelAccess> channelAccess,
        Map<String, ChannelCredential> channelCredentials,
        Map<String, RouteAccess> routes,
        String contentHash,
        Instant publishedAt
) {
    public StoredPolicySnapshot {
        endpoints = endpoints == null ? null : List.copyOf(endpoints);
        clientApps = clientApps == null ? null : Map.copyOf(clientApps);
        channelAccess = channelAccess == null ? null : Map.copyOf(channelAccess);
        channelCredentials = channelCredentials == null ? null : Map.copyOf(channelCredentials);
        routes = routes == null ? null : Map.copyOf(routes);
    }

    public record Endpoint(String endpointId, String ownerService, List<Matcher> matchers, String deliveryRouteId) {
        public Endpoint {
            matchers = matchers == null ? null : List.copyOf(matchers);
        }
    }

    public record Matcher(String httpMethod, String pathPattern) { }

    public record ClientApp(ClientAppStatus status, String platform) { }

    public record ChannelAccess(Set<String> allowedClientAppIds) {
        public ChannelAccess {
            allowedClientAppIds = allowedClientAppIds == null ? null : Set.copyOf(allowedClientAppIds);
        }
    }

    public record ChannelCredential(Credential activeCredential, Credential graceCredential) { }

    public record Credential(
            String credentialId,
            Instant validFrom,
            Instant expiresAt,
            SignaturePayloadContract signaturePayloadContract,
            String secretRef
    ) { }

    public record RouteAccess(
            ChannelCredentialRequirement channelCredentialRequirement,
            ChannelRequirement channelRequirement,
            SubjectRequirement subjectRequirement,
            Set<String> requiredScopes,
            Set<String> requiredAudiences,
            Forwarding forwarding,
            boolean enabled
    ) {
        public RouteAccess {
            requiredScopes = requiredScopes == null ? null : Set.copyOf(requiredScopes);
            requiredAudiences = requiredAudiences == null ? null : Set.copyOf(requiredAudiences);
        }
    }

    public record Forwarding(
            AuthorizationHeaderPolicy authorizationHeader,
            CookieForwardingMode cookies,
            Set<String> allowedCookieNames
    ) {
        public Forwarding {
            allowedCookieNames = allowedCookieNames == null ? null : Set.copyOf(allowedCookieNames);
        }
    }
}
