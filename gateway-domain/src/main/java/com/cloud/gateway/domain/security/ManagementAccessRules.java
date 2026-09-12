package com.cloud.gateway.domain.security;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;

import java.util.Set;

/** Independent management protection; the same subset rules apply to endpoint authorization. */
public record ManagementAccessRules(String audience) {
    public static final String ADMIN_SCOPE = "admin";

    public ManagementAccessRules {
        Require.notBlank(audience, DomainException::missingField);
    }

    public boolean allows(SubjectType type, Set<String> scopes, Set<String> audiences) {
        return type == SubjectType.HOST_SESSION
                && SubjectAccessRules.hasRequiredScopes(scopes, Set.of(ADMIN_SCOPE))
                && SubjectAccessRules.hasRequiredAudiences(audiences, Set.of(audience));
    }

    public void validatePolicy(EndpointPolicy policy) {
        Require.notNull(policy, DomainException::missingField);
        if (policy.subjectAuthencationRequirement() != SubjectAuthenticationRequirement.HOST_SESSION_REQUIRED
                || !SubjectAccessRules.hasRequiredScopes(policy.requiredScopes(), Set.of(ADMIN_SCOPE))
                || !SubjectAccessRules.hasRequiredAudiences(policy.requiredAudiences(), Set.of(audience))) {
            throw DomainException.invalidField();
        }
    }
}
