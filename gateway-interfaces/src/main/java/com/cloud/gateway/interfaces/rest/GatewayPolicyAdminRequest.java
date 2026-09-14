package com.cloud.gateway.interfaces.rest;

import com.cloud.framework.core.AuthenticatedSessionClientRequest;
import com.cloud.gateway.api.enums.AppStatusApiEnum;
import com.cloud.gateway.api.enums.ClientStatusApiEnum;
import com.cloud.gateway.api.enums.ClientVerificationRequirementApiEnum;
import com.cloud.gateway.api.enums.EndpointStatusApiEnum;
import com.cloud.gateway.api.enums.SignatureContractApiEnum;
import com.cloud.gateway.api.enums.SubjectAuthenticationRequirementApiEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

public final class GatewayPolicyAdminRequest {
    private GatewayPolicyAdminRequest() { }

    @Getter @Setter
    public static final class SaveAppAccess extends AuthenticatedSessionClientRequest {
        @NotBlank private String expectedSourceRevision;
        @NotBlank @JsonProperty("appId") private String targetAppId;
        @NotBlank private String platform;
        @NotNull private final Set<@NotBlank String> allowedOrigins = new LinkedHashSet<>();
    }

    @Getter @Setter
    public static final class SaveClientAccess extends AuthenticatedSessionClientRequest {
        @NotBlank private String expectedSourceRevision;
        @NotBlank private String clientId;
        @NotNull private final Set<@NotBlank String> allowedAppIds = new LinkedHashSet<>();
    }

    @Getter @Setter
    public static final class SaveClientCredential extends AuthenticatedSessionClientRequest {
        @NotBlank private String expectedSourceRevision;
        @NotBlank private String clientId;
        @NotNull @Valid private ClientCredential activeCredential;
        @Valid private ClientCredential graceCredential;
    }

    @Getter @Setter
    public static final class SaveEndpointAccess extends AuthenticatedSessionClientRequest {
        @NotBlank private String expectedSourceRevision;
        @NotBlank private String endpointId;
        @NotBlank private String serviceId;
        @NotBlank private String routeId;
        @NotEmpty @Valid private final List<Matcher> matchers = new java.util.ArrayList<>();
        @NotNull private final Set<@NotBlank String> tags = new LinkedHashSet<>();
        @NotNull private ClientVerificationRequirementApiEnum clientVerificationRequirement;
        @NotNull private SubjectAuthenticationRequirementApiEnum subjectAuthenticationRequirement;
        @NotNull private final Set<@NotBlank String> requiredScopes = new LinkedHashSet<>();
        @NotNull private final Set<@NotBlank String> requiredAudiences = new LinkedHashSet<>();
    }

    @Getter @Setter
    public static final class Matcher {
        @NotBlank private String httpMethod;
        @NotBlank private String pathPattern;
    }

    @Getter @Setter
    public static final class SetAppStatus extends AuthenticatedSessionClientRequest {
        @NotBlank private String expectedSourceRevision;
        @NotBlank private String appId;
        @NotNull private AppStatusApiEnum status;
    }

    @Getter @Setter
    public static final class SetClientStatus extends AuthenticatedSessionClientRequest {
        @NotBlank private String expectedSourceRevision;
        @NotBlank private String clientId;
        @NotNull private ClientStatusApiEnum status;
    }

    @Getter @Setter
    public static final class SetEndpointStatus extends AuthenticatedSessionClientRequest {
        @NotBlank private String expectedSourceRevision;
        @NotBlank private String endpointId;
        @NotNull private EndpointStatusApiEnum status;
    }

    @Getter @Setter
    public static final class RollbackAccessSnapshot extends AuthenticatedSessionClientRequest {
        @Positive private long targetSnapshotVersion;
    }

    @Getter @Setter
    public static final class PublishAccessSnapshot extends AuthenticatedSessionClientRequest { }

    @Getter @Setter
    public static final class ClientCredential {
        @NotBlank private String credentialId;
        @NotNull private Instant validFrom;
        @NotNull @Future private Instant expiresAt;
        @NotNull private SignatureContractApiEnum signatureContract;
        @NotBlank private String secretRef;
    }
}
