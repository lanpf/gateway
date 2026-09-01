package com.cloud.gateway.domain.policy;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import java.util.Set;

public record CredentialForwardingPolicy(
        AuthorizationHeaderPolicy authorizationHeader,
        CookieForwardingMode cookies,
        Set<String> allowedCookieNames
) {
    public CredentialForwardingPolicy {
        Require.notNull(authorizationHeader, DomainException::missingField);
        Require.notNull(cookies, DomainException::missingField);
        allowedCookieNames = Set.copyOf(Require.notNull(allowedCookieNames, DomainException::missingField));
        if (cookies == CookieForwardingMode.STRIP_ALL && !allowedCookieNames.isEmpty()) {
            throw invalid();
        }
        if (cookies == CookieForwardingMode.ALLOW_LIST && allowedCookieNames.isEmpty()) {
            throw invalid();
        }
    }

    public static CredentialForwardingPolicy stripAll() {
        return new CredentialForwardingPolicy(
                AuthorizationHeaderPolicy.STRIP, CookieForwardingMode.STRIP_ALL, Set.of());
    }

    private static DomainException invalid() {
        return DomainException.invalidField();
    }
}
