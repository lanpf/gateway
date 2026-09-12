package com.cloud.gateway.domain.policy.header;

import com.cloud.framework.core.RequestHeader;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Fixed trusted outputs and ordinary operations; deployment admission is validated by HeaderRules. */
public record HeaderPolicy(Set<RequestHeader> trustedHeaders,
        Map<String, String> setHeaders,
        Set<String> removeHeaders,
        CredentialForwarding credentialForwarding
) {
    public static final String GATEWAY_PREFIX = "x-gateway-";

    public HeaderPolicy {
        trustedHeaders = trustedHeaders == null ? Set.of() : Set.copyOf(trustedHeaders);
        trustedHeaders.forEach(header -> Require.notNull(header, DomainException::invalidField));

        setHeaders = setHeaders == null ? Map.of() : Map.copyOf(setHeaders);
        setHeaders.forEach((name, value) -> {
            HeaderRules.normalizeName(name);
            HeaderValueRules.requireValid(value);
        });
        if (removeHeaders != null) {
            removeHeaders.forEach(HeaderRules::normalizeName);
        }
        trustedHeaders = Set.copyOf(trustedHeaders == null ? Set.of() : trustedHeaders);
        setHeaders = Map.copyOf(setHeaders == null ? Map.of() : setHeaders);
        removeHeaders = Set.copyOf(removeHeaders == null ? Set.of() : removeHeaders);
        credentialForwarding = credentialForwarding == null ? CredentialForwarding.stripAll() : credentialForwarding;
        Set<String> targets = new HashSet<>();
        trustedHeaders.forEach(header -> {
            if (!TrustedHeaderResolver.supports(header)) {
                throw DomainException.invalidField();
            }
            targets.add(HeaderRules.normalizeName(header.headerName()));
        });
        setHeaders.forEach((name, value) -> {
            if (!targets.add(HeaderRules.normalizeName(name)) || RequestHeader.fromName(name).isPresent()) {
                throw DomainException.invalidField();
            }
        });
        removeHeaders.forEach(name -> {
            if (!targets.add(HeaderRules.normalizeName(name)) || RequestHeader.fromName(name).isPresent()) {
                throw DomainException.invalidField();
            }
        });
    }

    public static HeaderPolicy defaults(CredentialForwarding forwarding) {
        return new HeaderPolicy(TrustedHeaderResolver.supportedHeaders(), Map.of(), Set.of(), forwarding);
    }
}
