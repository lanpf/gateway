package com.cloud.gateway.domain.policy.header;

import com.cloud.framework.core.RequestHeader;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.policy.bundle.PolicyConfig;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Immutable deployment-owned name admission and operation restrictions. */
public record HeaderRules(
        Set<String> extraHeaders,
        Set<String> forbiddenSetHeaders,
        Set<String> forbiddenRemoveHeaders
) {
    public HeaderRules {
        extraHeaders = normalize(extraHeaders);
        forbiddenSetHeaders = normalize(forbiddenSetHeaders);
        forbiddenRemoveHeaders = normalize(forbiddenRemoveHeaders);
    }

    private Set<String> normalize(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            String name = Require.that(value,
                    v -> v.length() <= 128 && v.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+"),
                    DomainException::invalidField
            ).trim().toLowerCase(Locale.ROOT);
            if (!normalized.add(name)) {
                throw DomainException.invalidField();
            }
        }
        return Set.copyOf(normalized);
    }

    public static HeaderRules registeredOnly() {
        return new HeaderRules(Set.of(), Set.of(), Set.of());
    }

    public void validate(PolicyConfig configuration) {
        configuration.bundles().values().forEach(bundle -> {
            if (bundle.headers() != null) {
                validate(bundle.headers());
            }
        });
    }

    public void validate(HeaderPolicy policy) {
        policy.setHeaders().keySet().forEach(name -> validateOperation(name, forbiddenSetHeaders));
        policy.removeHeaders().forEach(name -> validateOperation(name, forbiddenRemoveHeaders));
    }

    private void validateOperation(String name, Set<String> forbidden) {
        String normalized = normalizeName(name);
        boolean admitted = extraHeaders.contains(normalized)
                || normalized.startsWith(HeaderPolicy.GATEWAY_PREFIX);
        if (RequestHeader.fromName(name).isPresent() || !admitted || forbidden.contains(normalized)) {
            throw DomainException.invalidField();
        }
    }

    public static String normalizeName(String name) {
        return Require.that(name,
                v -> v.length() <= 128 && v.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+"),
                DomainException::invalidField
        ).toLowerCase(Locale.ROOT);
    }

}
