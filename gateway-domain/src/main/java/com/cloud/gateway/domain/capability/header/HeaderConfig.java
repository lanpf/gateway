package com.cloud.gateway.domain.capability.header;

import com.cloud.framework.core.RequestHeader;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.util.NullSafe;
import com.cloud.gateway.domain.capability.CapabilityConfig;
import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Fixed trusted outputs and normalized ordinary operations; values are validated at construction,
 * deployment admission is validated by HeaderConfigRule. */
public record HeaderConfig(
        Set<RequestHeader> trustedHeaders,
        Map<String, String> setHeaders,
        Set<String> removeHeaders,
        CredentialForwarding credentialForwarding
) implements CapabilityConfig {
    private static final int MAX_HEADER_VALUE_LENGTH = 4096;

    public HeaderConfig {
        Set<String> normalized = new HashSet<>();

        trustedHeaders = NullSafe.copyOf(trustedHeaders, header -> {
            if (!TrustedHeaderResolver.supports(header)
                    || !normalized.add(RequestHeader.normalizeName(header.headerName()))) {
                throw invalid();
            }
        });

        Map<String, String> normalizedSet = new LinkedHashMap<>();
        NullSafe.forEach(setHeaders, (name, value) -> {
            if (RequestHeader.fromName(name).isPresent()
                    || value.length() > MAX_HEADER_VALUE_LENGTH
                    || value.codePoints().anyMatch(Character::isISOControl)
                    || !normalized.add(RequestHeader.normalizeName(name))) {
                throw invalid();
            }
            normalizedSet.put(RequestHeader.normalizeName(name), value);
        });
        setHeaders = NullSafe.copyOf(normalizedSet);

        Set<String> normalizedRemove = new HashSet<>();
        NullSafe.forEach(removeHeaders, name -> {
            if (RequestHeader.fromName(name).isPresent()
                    || !normalized.add(RequestHeader.normalizeName(name))) {
                throw invalid();
            }
            normalizedRemove.add(RequestHeader.normalizeName(name));
        });
        removeHeaders = NullSafe.copyOf(normalizedRemove);

        credentialForwarding = credentialForwarding == null ? CredentialForwarding.stripAll() : credentialForwarding;
    }

    public static HeaderConfig defaults() {
        return defaults(null);
    }

    public static HeaderConfig defaults(CredentialForwarding forwarding) {
        return new HeaderConfig(TrustedHeaderResolver.supportedHeaders(), Map.of(), Set.of(), forwarding);
    }
    @Override
    public CapabilityId id() { return BuiltInCapabilityCatalog.HEADER_APPLY; }

    private static DomainException invalid() {
        return new DomainException(DomainError.ACCESS_SNAPSHOT_POLICY_INVALID);
    }
}
