package com.cloud.gateway.domain.capability.header;

import com.cloud.framework.core.RequestHeader;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.capability.header.rules.HeaderNameRules;
import com.cloud.gateway.domain.capability.header.rules.HeaderValueRules;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Fixed trusted outputs and ordinary operations; name/value formats live in HeaderNameRules/HeaderValueRules,
 * deployment admission is validated by HeaderAdmissionRules. */
public record HeaderPolicy(Set<RequestHeader> trustedHeaders,
        Map<String, String> setHeaders,
        Set<String> removeHeaders,
        CredentialForwarding credentialForwarding
) implements com.cloud.gateway.domain.capability.CapabilityConfig {
    public static final String GATEWAY_PREFIX = "x-gw-";

    public HeaderPolicy {
        Set<String> normalizedHeaders = new HashSet<>();


        trustedHeaders = trustedHeaders == null ? Set.of() : trustedHeaders;
        trustedHeaders.forEach(header -> {
            Require.notNull(header, DomainException::missingField);
            if (!TrustedHeaderResolver.supports(header)) {
                throw DomainException.invalidObjectField();
            }
            String normalized = HeaderNameRules.normalize(header.headerName());
            normalizedHeaders.add(normalized);
        });
        trustedHeaders = Set.copyOf(trustedHeaders);

        setHeaders = setHeaders == null ? Map.of() : setHeaders;
        setHeaders.forEach((name, value) -> {
            String normalized = HeaderNameRules.normalize(name);
            HeaderValueRules.validate(value);
            if (!normalizedHeaders.add(normalized) || RequestHeader.fromName(normalized).isPresent()) {
                throw DomainException.invalidObjectField();
            }
        });
        setHeaders = Map.copyOf(setHeaders);

        removeHeaders = removeHeaders == null ? Set.of() : removeHeaders;
        removeHeaders.forEach(name -> {
            String normalized = HeaderNameRules.normalize(name);
            if (!normalizedHeaders.add(normalized) || RequestHeader.fromName(normalized).isPresent()) {
                throw DomainException.invalidObjectField();
            }
        });
        removeHeaders = Set.copyOf(removeHeaders);

        credentialForwarding = credentialForwarding == null ? CredentialForwarding.stripAll() : credentialForwarding;
    }

    public static HeaderPolicy defaults(CredentialForwarding forwarding) {
        return new HeaderPolicy(TrustedHeaderResolver.supportedHeaders(), Map.of(), Set.of(), forwarding);
    }
    @Override
    public com.cloud.gateway.domain.capability.CapabilityId capabilityId() { return HeaderConfigDefinition.ID; }
}
