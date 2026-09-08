package com.cloud.gateway.domain.policy;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record IngressClientAppPolicy(
        ClientAppId clientAppId,
        ClientAppStatus status,
        String platform,
        Set<String> allowedBrowserOrigins
) {
    public IngressClientAppPolicy {
        Require.notNull(clientAppId, DomainException::missingField);
        Require.notNull(status, DomainException::missingField);
        platform = Require.notBlank(platform, DomainException::missingField).trim();
        allowedBrowserOrigins = allowedBrowserOrigins == null
                ? Set.of()
                : allowedBrowserOrigins.stream()
                        .map(IngressClientAppPolicy::normalizeOrigin)
                        .collect(Collectors.toUnmodifiableSet());
    }

    public boolean enabled() { return status == ClientAppStatus.ENABLED; }

    public boolean allowsBrowserOrigin(String origin) {
        if (StringUtils.isBlank(origin)) {
            return false;
        }
        try {
            return allowedBrowserOrigins.contains(normalizeOrigin(origin));
        } catch (DomainException exception) {
            return false;
        }
    }

    private static String normalizeOrigin(String origin) {
        String value = Require.notBlank(origin, DomainException::missingField).trim();
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
                throw DomainException.invalidField();
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
            throw DomainException.invalidField();
        }
    }
}
