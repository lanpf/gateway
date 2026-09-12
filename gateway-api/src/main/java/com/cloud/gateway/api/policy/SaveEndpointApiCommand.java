package com.cloud.gateway.api.policy;

import com.cloud.framework.core.Request;
import com.cloud.gateway.api.enums.ClientVerificationModeApiEnum;
import com.cloud.gateway.api.enums.ClientRequirementApiEnum;
import com.cloud.gateway.api.enums.SubjectRequirementApiEnum;
import com.cloud.gateway.api.enums.EndpointStatusApiEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

public record SaveEndpointApiCommand(
        @NotBlank String expectedSourceRevision,
        @NotBlank String endpointId,
        @NotBlank String serviceId,
        @NotBlank String routeId,
        @NotEmpty @Valid List<Matcher> matchers,
        Set<@NotBlank String> tags,
        @NotNull EndpointStatusApiEnum status,
        @NotNull ClientRequirementApiEnum clientRequirement,
        @NotNull SubjectRequirementApiEnum subjectRequirement,
        Set<@NotBlank String> requiredScopes,
        Set<@NotBlank String> requiredAudiences
) implements Request {
    public SaveEndpointApiCommand {
        requiredScopes = requiredScopes == null ? null : Set.copyOf(requiredScopes);
        requiredAudiences = requiredAudiences == null ? null : Set.copyOf(requiredAudiences);
    }

    public record Matcher(@NotBlank String httpMethod, @NotBlank String pathPattern) { }
}
