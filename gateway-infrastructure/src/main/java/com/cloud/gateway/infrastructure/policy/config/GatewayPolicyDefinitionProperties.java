package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.gateway.domain.endpoint.SubjectAuthencationRequirement;
import com.cloud.gateway.domain.policy.AuthorizationHeaderForwardingMode;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.client.ClientStatus;
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
@ConfigurationProperties(prefix = GatewayPolicyDefinitionProperties.PREFIX, ignoreUnknownFields = false)
@Getter
public class GatewayPolicyDefinitionProperties {
    public static final String PREFIX = "gateway.policy-definition";

    @Valid private final PolicyCompositionProperties policies = new PolicyCompositionProperties();

    @NotEmpty @Valid private final List<Endpoint> endpoints = new ArrayList<>();
    @NotEmpty @Valid private final Map<@NotBlank String, App> apps = new LinkedHashMap<>();
    @Valid private final Map<@NotBlank String, Client> clients = new LinkedHashMap<>();
    @NotEmpty @Valid private final Map<@NotBlank String, EndpointAccess> routes = new LinkedHashMap<>();

    public List<StoredPolicySnapshot.Endpoint> toStoredEndpoints() {
        return endpoints.stream().map(Endpoint::toStored).toList();
    }

    public Map<String, StoredPolicySnapshot.App> toStoredApps() {
        return apps.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().toStored()));
    }

    public Map<String, StoredPolicySnapshot.Route> toStoredRoutes() {
        return routes.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().toStored()));
    }

    @Getter
    @Setter
    public static class Endpoint {
        @NotBlank private String endpointId;
        @NotBlank private String serviceId;
        @NotEmpty @Valid private final List<Matcher> matchers = new ArrayList<>();
        @NotBlank private String routeId;
        private final Set<@NotBlank String> tags = new LinkedHashSet<>();

        private StoredPolicySnapshot.Endpoint toStored() {
            return new StoredPolicySnapshot.Endpoint(endpointId, serviceId,
                    matchers.stream().map(Matcher::toStored).toList(), routeId, tags);
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
    public static class App {
        @NotNull private PolicyStatus status = PolicyStatus.ENABLED;
        @NotBlank private String platform;
        @NotNull private final Set<@NotBlank String> allowedOrigins = new LinkedHashSet<>();

        private StoredPolicySnapshot.App toStored() {
            return new StoredPolicySnapshot.App(status, platform, allowedOrigins);
        }
    }

    @Getter
    @Setter
    public static class Client {
        @NotNull private ClientStatus status;
        @NotNull private final Set<@NotBlank String> allowedAppIds = new LinkedHashSet<>();
        @Valid private ClientCredentials credentials;
    }

    @Getter
    @Setter
    public static class ClientCredentials {
        @NotNull @Valid private Credential activeCredential;
        @Valid private Credential graceCredential;

    }

    @Getter
    @Setter
    public static class Credential {
        @NotBlank private String credentialId;
        @NotNull private Instant validFrom;
        @NotNull private Instant expiresAt;
        @NotNull private SignatureContract signatureContract;
        @NotBlank private String secretRef;

        private StoredPolicySnapshot.Credential toStored() {
            return new StoredPolicySnapshot.Credential(
                    credentialId, validFrom, expiresAt, signatureContract, secretRef);
        }
    }

    @Getter
    @Setter
    public static class EndpointAccess {
        @NotNull private ClientVerificationMode clientVerificationMode =
                ClientVerificationMode.IGNORE;
        @NotNull private ClientVerificationRequirement clientVerificationRequirement = ClientVerificationRequirement.FORBIDDEN;
        @NotNull private SubjectAuthencationRequirement subjectAuthencationRequirement = SubjectAuthencationRequirement.IGNORE;
        @NotNull private final Set<String> requiredScopes = new LinkedHashSet<>();
        @NotNull private final Set<String> requiredAudiences = new LinkedHashSet<>();
        @NotNull @Valid private final Forwarding credentialForwarding = new Forwarding();
        @NotNull private EndpointStatus status = EndpointStatus.ENABLED;

        private StoredPolicySnapshot.Route toStored() {
            return new StoredPolicySnapshot.Route(clientVerificationMode, clientVerificationRequirement,
                    subjectAuthencationRequirement, requiredScopes, requiredAudiences, credentialForwarding.toStored(), status);
        }
    }

    @Getter
    @Setter
    public static class Forwarding {
        @NotNull private AuthorizationHeaderForwardingMode authorizationHeader = AuthorizationHeaderForwardingMode.STRIP;
        @NotNull private CookieForwardingMode cookies = CookieForwardingMode.STRIP_ALL;
        @NotNull private final Set<String> allowedCookieNames = new LinkedHashSet<>();

        private StoredPolicySnapshot.Forwarding toStored() {
            return new StoredPolicySnapshot.Forwarding(authorizationHeader, cookies, allowedCookieNames);
        }
    }
}
