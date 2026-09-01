package com.cloud.gateway.api.policy;

import com.cloud.framework.core.Request;
import com.cloud.gateway.api.enums.ChannelCredentialRequirementApiEnum;
import com.cloud.gateway.api.enums.ChannelRequirementApiEnum;
import com.cloud.gateway.api.enums.SubjectRequirementApiEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record SaveRouteAccessPolicyApiCommand(
        @NotBlank String expectedSourceRevision,
        @NotBlank String endpointId,
        @NotNull ChannelCredentialRequirementApiEnum channelCredentialRequirement,
        @NotNull ChannelRequirementApiEnum channelRequirement,
        @NotNull SubjectRequirementApiEnum subjectRequirement,
        @NotNull Set<String> requiredScopes,
        @NotNull Set<String> requiredAudiences,
        @NotNull @Valid CredentialForwardingApiCommand credentialForwardingPolicy,
        boolean enabled
) implements Request {
    public SaveRouteAccessPolicyApiCommand {
        requiredScopes = requiredScopes == null ? null : Set.copyOf(requiredScopes);
        requiredAudiences = requiredAudiences == null ? null : Set.copyOf(requiredAudiences);
    }
}
