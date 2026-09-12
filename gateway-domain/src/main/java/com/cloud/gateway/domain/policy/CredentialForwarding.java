package com.cloud.gateway.domain.policy;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

import java.util.Set;

public record CredentialForwarding(
        AuthorizationHeaderForwardingMode authorizationHeader,
        CookieForwardingMode cookies,
        Set<String> allowedCookieNames
) {
    public CredentialForwarding {
        Require.notNull(authorizationHeader, DomainException::missingField);
        Require.notNull(cookies, DomainException::missingField);

        allowedCookieNames = allowedCookieNames == null ? Set.of() : allowedCookieNames;
        allowedCookieNames.forEach(value -> Require.notBlank(value, DomainException::missingField));
        allowedCookieNames = Set.copyOf(allowedCookieNames);

        if (cookies == CookieForwardingMode.STRIP_ALL && !allowedCookieNames.isEmpty()) {
            throw invalid();
        }
        if (cookies == CookieForwardingMode.ALLOW_LIST && allowedCookieNames.isEmpty()) {
            throw invalid();
        }
    }

    public static CredentialForwarding stripAll() {
        return new CredentialForwarding(
                AuthorizationHeaderForwardingMode.STRIP,
                CookieForwardingMode.STRIP_ALL,
                Set.of());
    }

    private static DomainException invalid() {
        return DomainException.invalidField();
    }
}
