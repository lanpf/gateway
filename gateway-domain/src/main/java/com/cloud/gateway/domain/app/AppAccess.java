package com.cloud.gateway.domain.app;

import com.cloud.framework.core.validation.Require;

import com.cloud.gateway.domain.common.DomainException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record AppAccess(Set<String> allowedOrigins) {
    public AppAccess {
        allowedOrigins = allowedOrigins == null
                ? Set.of()
                : allowedOrigins.stream()
                .map(AppAccess::normalizeOrigin)
                .collect(Collectors.toUnmodifiableSet());
    }

    boolean allowsOrigin(String origin) {
        try {
            return allowedOrigins.contains(normalizeOrigin(origin));
        } catch (DomainException exception) {
            return false;
        }
    }

    private static String normalizeOrigin(String origin) {
        String value = Require.notBlank(origin, DomainException::invalidObjectField).trim();
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
                throw DomainException.invalidObjectField();
            }
            int port = uri.getPort();
            if ((scheme.equalsIgnoreCase("https") && port == 443)
                    || (scheme.equalsIgnoreCase("http") && port == 80)) {
                port = -1;
            }
            return new URI(
                    scheme.toLowerCase(Locale.ROOT),
                    null,
                    uri.getHost().toLowerCase(Locale.ROOT),
                    port,
                    null,
                    null,
                    null).toASCIIString();
        } catch (URISyntaxException exception) {
            throw DomainException.invalidObjectField();
        }
    }
}
