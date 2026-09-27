package com.cloud.gateway.domain.endpoint;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.validation.Require;

import java.util.Set;

public record EndpointAccess(
        ClientVerificationRequirement clientVerificationRequirement,
        SubjectAuthenticationRequirement subjectAuthenticationRequirement,
        Set<String> requiredScopes,
        Set<String> requiredAudiences
) {
    public EndpointAccess {
        Require.notNull(clientVerificationRequirement, DomainException::missingField);
        Require.notNull(subjectAuthenticationRequirement, DomainException::missingField);

        requiredScopes = requiredScopes == null ? Set.of() : requiredScopes;
        requiredScopes.forEach(value -> Require.notBlank(value, DomainException::invalidObjectField));
        requiredScopes = Set.copyOf(requiredScopes);

        requiredAudiences = requiredAudiences == null ? Set.of() : requiredAudiences;
        requiredAudiences.forEach(value -> Require.notBlank(value, DomainException::invalidObjectField));
        requiredAudiences = Set.copyOf(requiredAudiences);
    }
}
