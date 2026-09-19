package com.cloud.gateway.domain.ingress.access.subject;

import com.cloud.framework.core.SubjectType;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.Set;

public record AuthenticatedSubject(
        SubjectType subjectType,
        SubjectId subjectId,
        SessionId sessionId,
        String issuer,
        Set<String> audiences,
        Set<String> scopes,
        Instant authenticationExpiresAt
) {
    public AuthenticatedSubject {
        Require.notNull(subjectType, DomainException::missingField);
        Require.notNull(subjectId, DomainException::missingField);
        Require.notNull(sessionId, DomainException::missingField);
        Require.notNull(authenticationExpiresAt, DomainException::missingField);

        switch (subjectType) {
            case HOST_SESSION -> {
                issuer = Require.notBlank(issuer, DomainException::missingField).trim();

                audiences = audiences == null ? Set.of() : audiences;
                audiences.forEach(value -> Require.notBlank(value, DomainException::invalidField));
                audiences = Set.copyOf(audiences);

                scopes = scopes == null ? Set.of() : scopes;
                scopes.forEach(value -> Require.notBlank(value, DomainException::invalidField));
                scopes = Set.copyOf(scopes);
            }
            case BROWSER_SESSION -> {
                if (StringUtils.isNotBlank(issuer) || CollectionUtils.isNotEmpty(audiences) || CollectionUtils.isNotEmpty(scopes)) {
                    throw DomainException.invalidField();
                }

                issuer = null;
                audiences = Set.of();
                scopes = Set.of();
            }
        }
    }
}
