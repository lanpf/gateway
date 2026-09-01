package com.cloud.gateway.interfaces.rest;

import com.cloud.framework.core.AuthenticatedSessionClientRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.cloud.gateway.api.enums.AuthorizationHeaderPolicyApiEnum;
import com.cloud.gateway.api.enums.ChannelCredentialRequirementApiEnum;
import com.cloud.gateway.api.enums.ChannelRequirementApiEnum;
import com.cloud.gateway.api.enums.ClientAppStatusApiEnum;
import com.cloud.gateway.api.enums.CookieForwardingModeApiEnum;
import com.cloud.gateway.api.enums.SignaturePayloadContractApiEnum;
import com.cloud.gateway.api.enums.SubjectRequirementApiEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

final class GatewayPolicyAdminRequest {
    private static final String CLIENT_APP_ID_PROPERTY = "clientAppId";

    private GatewayPolicyAdminRequest() { }

    @Getter
    @Setter
    static final class SaveClientAppPolicy extends AuthenticatedSessionClientRequest {
        @NotBlank private String expectedSourceRevision;
        @NotBlank @JsonProperty(CLIENT_APP_ID_PROPERTY) private String targetClientAppId;
        @NotNull private ClientAppStatusApiEnum status;
        @NotBlank private String platform;

    }

    @Getter
    @Setter
    static final class SaveChannelAccessPolicy extends AuthenticatedSessionClientRequest {
        @NotBlank private String expectedSourceRevision;
        @NotBlank private String channelCode;
        @NotNull private Set<@NotBlank String> allowedClientAppIds;

    }

    @Getter
    @Setter
    static final class SaveChannelCredentialPolicy extends AuthenticatedSessionClientRequest {
        @NotBlank private String expectedSourceRevision;
        @NotBlank private String channelCode;
        @NotNull @Valid private ChannelCredential activeCredential;
        @Valid private ChannelCredential graceCredential;

    }

    @Getter
    @Setter
    static final class SaveRouteAccessPolicy extends AuthenticatedSessionClientRequest {
        @NotBlank private String expectedSourceRevision;
        @NotBlank private String endpointId;
        @NotNull private ChannelCredentialRequirementApiEnum channelCredentialRequirement;
        @NotNull private ChannelRequirementApiEnum channelRequirement;
        @NotNull private SubjectRequirementApiEnum subjectRequirement;
        @NotNull private Set<String> requiredScopes;
        @NotNull private Set<String> requiredAudiences;
        @NotNull @Valid private CredentialForwarding credentialForwardingPolicy;
        private boolean enabled;

    }

    @Getter
    @Setter
    static final class RollbackPolicySnapshot extends AuthenticatedSessionClientRequest {
        @PositiveOrZero private long targetSnapshotVersion;
    }

    @Getter
    @Setter
    static final class PublishPolicySnapshot extends AuthenticatedSessionClientRequest { }

    @Getter
    @Setter
    static final class ChannelCredential {
        @NotBlank private String credentialId;
        @NotNull private Instant validFrom;
        @NotNull @Future private Instant expiresAt;
        @NotNull private SignaturePayloadContractApiEnum signaturePayloadContract;
        @NotBlank private String secretRef;

    }

    @Getter
    @Setter
    static final class CredentialForwarding {
        @NotNull private AuthorizationHeaderPolicyApiEnum authorizationHeader;
        @NotNull private CookieForwardingModeApiEnum cookies;
        @NotNull private Set<String> allowedCookieNames;

    }
}
