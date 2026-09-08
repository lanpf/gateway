package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.gateway.domain.policy.AuthorizationHeaderPolicy;
import com.cloud.gateway.domain.policy.ChannelCredentialRequirement;
import com.cloud.gateway.domain.policy.ChannelRequirement;
import com.cloud.gateway.domain.policy.ClientAppStatus;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.policy.SignaturePayloadContract;
import com.cloud.gateway.domain.policy.SubjectRequirement;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(IngressPolicyDefinitionProperties.PREFIX)
@Getter
public class IngressPolicyDefinitionProperties {
    public static final String PREFIX = "gateway.ingress-policy";

    @NotEmpty @Valid private final List<Endpoint> endpoints = new ArrayList<>();
    @NotEmpty @Valid private final Map<@NotBlank String, ClientApp> clientApps = new LinkedHashMap<>();
    @Valid private final Map<@NotBlank String, ChannelAccess> channelAccess = new LinkedHashMap<>();
    @Valid private final Map<@NotBlank String, ChannelCredential> channelCredentials = new LinkedHashMap<>();
    @NotEmpty @Valid private final Map<@NotBlank String, RouteAccess> routes = new LinkedHashMap<>();

    public List<StoredPolicySnapshot.Endpoint> toStoredEndpoints() {
        return endpoints.stream().map(Endpoint::toStored).toList();
    }

    public Map<String, StoredPolicySnapshot.ClientApp> toStoredClientApps() {
        return clientApps.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().toStored()));
    }

    public Map<String, StoredPolicySnapshot.ChannelAccess> toStoredChannelAccess() {
        return channelAccess.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().toStored()));
    }

    public Map<String, StoredPolicySnapshot.ChannelCredential> toStoredChannelCredentials() {
        return channelCredentials.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().toStored()));
    }

    public Map<String, StoredPolicySnapshot.RouteAccess> toStoredRoutes() {
        return routes.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().toStored()));
    }

    @Getter
    @Setter
    public static class Endpoint {
        @NotBlank private String endpointId;
        @NotBlank private String ownerService;
        @NotEmpty @Valid private final List<Matcher> matchers = new ArrayList<>();
        @NotBlank private String deliveryRouteId;

        private StoredPolicySnapshot.Endpoint toStored() {
            return new StoredPolicySnapshot.Endpoint(endpointId, ownerService,
                    matchers.stream().map(Matcher::toStored).toList(), deliveryRouteId);
        }
    }

    @Getter
    @Setter
    public static class Matcher {
        @NotBlank private String httpMethod;
        @NotBlank private String pathPattern;

        private StoredPolicySnapshot.Matcher toStored() {
            return new StoredPolicySnapshot.Matcher(httpMethod, pathPattern);
        }
    }

    @Getter
    @Setter
    public static class ClientApp {
        @NotNull private ClientAppStatus status = ClientAppStatus.ENABLED;
        @NotBlank private String platform;
        @NotNull private final Set<@NotBlank String> allowedBrowserOrigins = new LinkedHashSet<>();

        private StoredPolicySnapshot.ClientApp toStored() {
            return new StoredPolicySnapshot.ClientApp(status, platform, allowedBrowserOrigins);
        }
    }

    @Getter
    public static class ChannelAccess {
        @NotNull private final Set<@NotBlank String> allowedClientAppIds = new LinkedHashSet<>();

        private StoredPolicySnapshot.ChannelAccess toStored() {
            return new StoredPolicySnapshot.ChannelAccess(allowedClientAppIds);
        }
    }

    @Getter
    @Setter
    public static class ChannelCredential {
        @NotNull @Valid private Credential activeCredential;
        @Valid private Credential graceCredential;

        private StoredPolicySnapshot.ChannelCredential toStored() {
            return new StoredPolicySnapshot.ChannelCredential(
                    activeCredential.toStored(), graceCredential == null ? null : graceCredential.toStored());
        }
    }

    @Getter
    @Setter
    public static class Credential {
        @NotBlank private String credentialId;
        @NotNull private Instant validFrom;
        @NotNull private Instant expiresAt;
        @NotNull private SignaturePayloadContract signaturePayloadContract;
        @NotBlank private String secretRef;

        private StoredPolicySnapshot.Credential toStored() {
            return new StoredPolicySnapshot.Credential(
                    credentialId, validFrom, expiresAt, signaturePayloadContract, secretRef);
        }
    }

    @Getter
    @Setter
    public static class RouteAccess {
        @NotNull private ChannelCredentialRequirement channelCredentialRequirement =
                ChannelCredentialRequirement.IGNORE;
        @NotNull private ChannelRequirement channelRequirement = ChannelRequirement.FORBIDDEN;
        @NotNull private SubjectRequirement subjectRequirement = SubjectRequirement.IGNORE;
        @NotNull private final Set<String> requiredScopes = new LinkedHashSet<>();
        @NotNull private final Set<String> requiredAudiences = new LinkedHashSet<>();
        @NotNull @Valid private final Forwarding forwarding = new Forwarding();
        private boolean enabled = true;

        private StoredPolicySnapshot.RouteAccess toStored() {
            return new StoredPolicySnapshot.RouteAccess(channelCredentialRequirement, channelRequirement,
                    subjectRequirement, requiredScopes, requiredAudiences, forwarding.toStored(), enabled);
        }
    }

    @Getter
    @Setter
    public static class Forwarding {
        @NotNull private AuthorizationHeaderPolicy authorizationHeader = AuthorizationHeaderPolicy.STRIP;
        @NotNull private CookieForwardingMode cookies = CookieForwardingMode.STRIP_ALL;
        @NotNull private final Set<String> allowedCookieNames = new LinkedHashSet<>();

        private StoredPolicySnapshot.Forwarding toStored() {
            return new StoredPolicySnapshot.Forwarding(authorizationHeader, cookies, allowedCookieNames);
        }
    }
}
