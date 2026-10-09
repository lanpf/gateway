package com.cloud.gateway.domain.ingress.access.subject;

import com.cloud.framework.core.SubjectType;
import com.cloud.framework.core.error.FrameworkException;
import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;
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
        Require.allNotNull(subjectType, subjectId, sessionId, authenticationExpiresAt);

        switch (subjectType) {
            case HOST_SESSION -> {
                issuer = Require.notBlank(issuer).trim();
                audiences = NullSafe.copyOf(audiences, Require::notBlank);
                scopes = NullSafe.copyOf(scopes, Require::notBlank);
            }
            case BROWSER_SESSION -> {
                if (StringUtils.isNotBlank(issuer) || CollectionUtils.isNotEmpty(audiences) || CollectionUtils.isNotEmpty(scopes)) {
                    throw FrameworkException.invalidObject();
                }

                issuer = null;
                audiences = Set.of();
                scopes = Set.of();
            }
        }
    }
}
