package com.cloud.gateway.interfaces.rest;

import com.cloud.framework.core.AuthenticatedSessionClientRequest;
import com.cloud.gateway.api.enums.ClientVerificationModeApiEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.cloud.gateway.api.enums.AuthorizationHeaderPolicyApiEnum;
import com.cloud.gateway.api.enums.ClientRequirementApiEnum;
import com.cloud.gateway.api.enums.AppStatusApiEnum;
import com.cloud.gateway.api.enums.ClientStatusApiEnum;
import com.cloud.gateway.api.enums.CookieForwardingModeApiEnum;
import com.cloud.gateway.api.enums.SignatureContractApiEnum;
import com.cloud.gateway.api.enums.SubjectRequirementApiEnum;
import com.cloud.gateway.api.enums.EndpointStatusApiEnum;
import com.cloud.gateway.api.policy.PolicyCompositionApiCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

public final class GatewayPolicyAdminRequest {
    private static final String APP_ID_PROPERTY = "appId";

    private GatewayPolicyAdminRequest() { }

    @Getter @Setter
    public static final class SavePolicyComposition extends AuthenticatedSessionClientRequest {
        @NotBlank private String expectedSourceRevision;
        @NotNull @Valid private PolicyCompositionApiCommand policies;
    }

    @Getter
    @Setter
    public static final class SaveAppPolicy extends AuthenticatedSessionClientRequest {
        @NotBlank private String expectedSourceRevision;
        @NotBlank @JsonProperty(APP_ID_PROPERTY) private String targetAppId;
        @NotNull private AppStatusApiEnum status;
        @NotBlank private String platform;
        @NotNull private final Set<@NotBlank String> allowedOrigins = new LinkedHashSet<>();

    }

    @Getter
    @Setter
    public static final class SaveClientAccessPolicy extends AuthenticatedSessionClientRequest {
        @NotNull private ClientStatusApiEnum status;
        @NotBlank private String expectedSourceRevision;
        @NotBlank private String clientId;
        @NotNull private Set<@NotBlank String> allowedAppIds;

    }

    @Getter
    @Setter
    public static final class SaveClientCredentialPolicy extends AuthenticatedSessionClientRequest {
        @NotBlank private String expectedSourceRevision;
        @NotBlank private String clientId;
        @NotNull @Valid private ClientCredential activeCredential;
        @Valid private ClientCredential graceCredential;

    }

    @Getter
    @Setter
    public static final class SaveEndpointAccessPolicy extends AuthenticatedSessionClientRequest {
        @NotBlank private String expectedSourceRevision;
        @NotBlank private String endpointId;
        @NotNull private ClientVerificationModeApiEnum clientCredentialRequirement;
        @NotNull private ClientRequirementApiEnum clientRequirement;
        @NotNull private SubjectRequirementApiEnum subjectRequirement;
        @NotNull private Set<String> requiredScopes;
        @NotNull private Set<String> requiredAudiences;
        @NotNull @Valid private CredentialForwarding credentialForwarding;
        @NotNull private EndpointStatusApiEnum status;

    }

    @Getter
    @Setter
    public static final class RollbackPolicySnapshot extends AuthenticatedSessionClientRequest {
        @PositiveOrZero private long targetSnapshotVersion;
    }

    @Getter
    @Setter
    public static final class PublishPolicySnapshot extends AuthenticatedSessionClientRequest { }

    @Getter
    @Setter
    public static final class ClientCredential {
        @NotBlank private String credentialId;
        @NotNull private Instant validFrom;
        @NotNull @Future private Instant expiresAt;
        @NotNull private SignatureContractApiEnum signaturePayloadContract;
        @NotBlank private String secretRef;

    }

    @Getter
    @Setter
    public static final class CredentialForwarding {
        @NotNull private AuthorizationHeaderPolicyApiEnum authorizationHeader;
        @NotNull private CookieForwardingModeApiEnum cookies;
        @NotNull private Set<String> allowedCookieNames;

    }
}
