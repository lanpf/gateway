package com.cloud.gateway.domain.endpoint;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;

import java.util.Set;

public record EndpointAccess(
        ClientVerificationRequirement clientVerificationRequirement,
        SubjectAuthenticationRequirement subjectAuthenticationRequirement,
        Set<String> requiredScopes,
        Set<String> requiredAudiences
) {
    public EndpointAccess {
        Require.allNotNull(clientVerificationRequirement, subjectAuthenticationRequirement);

        requiredScopes = NullSafe.copyOf(requiredScopes, Require::notBlank);
        requiredAudiences = NullSafe.copyOf(requiredAudiences, Require::notBlank);
    }
}
