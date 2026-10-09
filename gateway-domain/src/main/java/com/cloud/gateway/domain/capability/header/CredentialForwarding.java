package com.cloud.gateway.domain.capability.header;

import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;

import java.util.Set;

public record CredentialForwarding(
        AuthorizationHeaderForwardingMode authorizationHeader,
        CookieForwardingMode cookies,
        Set<String> allowedCookieNames
) {
    public CredentialForwarding {
        Require.allNotNull(authorizationHeader, cookies);
        allowedCookieNames = NullSafe.copyOf(allowedCookieNames, Require::notBlank);

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
        return new DomainException(DomainError.ACCESS_SNAPSHOT_POLICY_INVALID);
    }
}
