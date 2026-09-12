package com.cloud.gateway.domain.policy.capability;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public record CachePolicy(Duration ttl, Duration staleWindow, int maxBodyBytes, Set<String> varyHeaders) {
    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9a-z-]+");

    public CachePolicy {
        Require.notNull(ttl, DomainException::missingField);
        Require.notNull(staleWindow, DomainException::missingField);
        if (ttl.compareTo(Duration.ofMillis(1)) < 0 || ttl.compareTo(Duration.ofDays(1)) > 0
                || staleWindow.isNegative() || staleWindow.compareTo(Duration.ofDays(1)) > 0
                || maxBodyBytes < 1 || maxBodyBytes > 16 * 1024 * 1024) {
            throw DomainException.invalidField();
        }
        TreeSet<String> normalized = new TreeSet<>();
        if (varyHeaders != null) {
            for (String header : varyHeaders) {
                Require.notNull(header, DomainException::missingField);
                String name = header.toLowerCase(Locale.ROOT);
                if (name.length() > 128 || !HEADER_NAME.matcher(name).matches()) {
                    throw DomainException.invalidField();
                }
                normalized.add(name);
            }
        }
        if (normalized.size() > 32) {
            throw DomainException.invalidField();
        }
        varyHeaders = Set.copyOf(normalized);
    }
}
