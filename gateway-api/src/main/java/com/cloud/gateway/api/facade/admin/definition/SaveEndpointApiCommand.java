package com.cloud.gateway.api.facade.admin.definition;

import com.cloud.framework.core.Request;
import com.cloud.gateway.api.enums.ClientVerificationRequirementApiEnum;
import com.cloud.gateway.api.enums.SubjectAuthenticationRequirementApiEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Content-only upsert of one endpoint's delivery identity and access policy. Status is not part of
 * this command: creation initializes it to ENABLED (content reaches runtime only after
 * publication), and an existing endpoint's status is never changed by a save — use setEndpointStatus.
 * <p>expectedSourceRevision is an opaque CAS token: null/empty asserts the endpoint-access definition
 * does not exist yet (initial creation); otherwise it must equal the revision returned by the last
 * read or save of that definition.
 */
public record SaveEndpointApiCommand(
        String expectedSourceRevision,
        @NotBlank String endpointId,
        @NotBlank String serviceId,
        @NotBlank String routeId,
        @NotEmpty @Valid List<Matcher> matchers,
        Set<@NotBlank String> tags,
        @NotNull ClientVerificationRequirementApiEnum clientVerificationRequirement,
        @NotNull SubjectAuthenticationRequirementApiEnum subjectAuthenticationRequirement,
        Set<@NotBlank String> requiredScopes,
        Set<@NotBlank String> requiredAudiences
) implements Request {
    public SaveEndpointApiCommand {
        requiredScopes = requiredScopes == null ? null : Set.copyOf(requiredScopes);
        requiredAudiences = requiredAudiences == null ? null : Set.copyOf(requiredAudiences);
    }

    public record Matcher(@NotBlank String httpMethod, @NotBlank String pathPattern) { }
}
