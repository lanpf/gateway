package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
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
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = GatewayPolicyDefinitionProperties.PREFIX, ignoreUnknownFields = false)
@Getter
public class GatewayPolicyDefinitionProperties {
    public static final String PREFIX = "gateway.access-definition";

    @Valid private final CapabilityPolicyConfigProperties policies = new CapabilityPolicyConfigProperties();

    @NotEmpty @Valid private final List<Endpoint> endpoints = new ArrayList<>();
    @NotEmpty @Valid private final Map<@NotBlank String, App> apps = new LinkedHashMap<>();
    @Valid private final Map<@NotBlank String, Client> clients = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Endpoint {
        @NotBlank private String endpointId;
        @NotBlank private String serviceId;
        @NotEmpty @Valid private final List<Matcher> matchers = new ArrayList<>();
        @NotBlank private String routeId;
        private final Set<@NotBlank String> tags = new LinkedHashSet<>();
        @NotNull private EndpointStatus status = EndpointStatus.ENABLED;
        @NotNull @Valid private Access access = new Access();
    }

    @Getter
    @Setter
    public static class Matcher {
        @NotBlank private String httpMethod;
        @NotBlank private String pathPattern;
    }

    @Getter
    @Setter
    public static class Access {
        @NotNull private ClientVerificationRequirement clientVerificationRequirement =
                ClientVerificationRequirement.REQUIRED;
        @NotNull private SubjectAuthenticationRequirement subjectAuthenticationRequirement =
                SubjectAuthenticationRequirement.ANONYMOUS;
        @NotNull private final Set<String> requiredScopes = new LinkedHashSet<>();
        @NotNull private final Set<String> requiredAudiences = new LinkedHashSet<>();
    }

    @Getter
    @Setter
    public static class App {
        @NotNull private AppStatus status = AppStatus.ENABLED;
        @NotBlank private String platform;
        @NotNull private final Set<String> allowedOrigins = new LinkedHashSet<>();
    }

    @Getter
    @Setter
    public static class Client {
        @NotNull private ClientStatus status;
        @NotNull private final Set<String> allowedAppIds = new LinkedHashSet<>();
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
    }
}
