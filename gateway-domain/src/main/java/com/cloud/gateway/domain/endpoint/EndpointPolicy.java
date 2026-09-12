package com.cloud.gateway.domain.endpoint;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

import java.util.Set;

public record EndpointPolicy(
        ClientVerificationRequirement clientVerificationRequirement,
        SubjectAuthencationRequirement subjectAuthencationRequirement,
        Set<String> requiredScopes,
        Set<String> requiredAudiences
) {
    public EndpointPolicy {
        Require.notNull(clientVerificationRequirement, DomainException::missingField);
        Require.notNull(subjectAuthencationRequirement, DomainException::missingField);

        requiredScopes = requiredScopes == null ? Set.of() : requiredScopes;
        requiredScopes.forEach(value -> Require.notBlank(value, DomainException::invalidField));
        requiredScopes = Set.copyOf(requiredScopes);

        requiredAudiences = requiredAudiences == null ? Set.of() : requiredAudiences;
        requiredAudiences.forEach(value -> Require.notBlank(value, DomainException::invalidField));
        requiredAudiences = Set.copyOf(requiredAudiences);
    }
}
