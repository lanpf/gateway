package com.cloud.gateway.domain.access;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.Set;

public record AuthenticatedSubject(
        SubjectType subjectType,
        String subjectId,
        String loginSessionId,
        String issuer,
        Set<String> audiences,
        Set<String> scopes,
        Instant credentialExpiresAt
) {
    public AuthenticatedSubject {
        Require.notNull(subjectType, DomainException::missingField);
        subjectId = Require.notBlank(subjectId, DomainException::missingField).trim();
        loginSessionId = Require.notBlank(loginSessionId, DomainException::missingField).trim();
        audiences = Set.copyOf(Require.notNull(audiences, DomainException::missingField));
        scopes = Set.copyOf(Require.notNull(scopes, DomainException::missingField));
        Require.notNull(credentialExpiresAt, DomainException::missingField);
        if (subjectType == SubjectType.HOST_SESSION) {
            issuer = Require.notBlank(issuer, DomainException::missingField).trim();
        } else {
            if (StringUtils.isNotBlank(issuer) || !audiences.isEmpty() || !scopes.isEmpty()) {
                throw DomainException.invalidField();
            }
            issuer = null;
        }
    }
}
