package com.cloud.gateway.domain.capability.header.rules;

import com.cloud.framework.core.validation.Require;

import com.cloud.framework.core.RequestHeader;
import com.cloud.gateway.domain.capability.header.HeaderPolicy;
import com.cloud.gateway.domain.common.DomainException;

import java.util.Set;

/** Immutable deployment-owned name admission and operation restrictions. */
public record HeaderAdmissionRules(
        Set<String> extra,
        Set<String> denySet,
        Set<String> denyRemove
) {
    public HeaderAdmissionRules {
        extra = normalized(extra);
        denySet = normalized(denySet);
        denyRemove = normalized(denyRemove);
    }

    private static Set<String> normalized(Set<String> names) {
        return names == null ? Set.of() : Set.copyOf(Require.noDuplicates(
                names.stream().map(HeaderNameRules::normalize).toList(), DomainException::invalidObjectField));
    }

    public static HeaderAdmissionRules registeredOnly() {
        return new HeaderAdmissionRules(Set.of(), Set.of(), Set.of());
    }

    public void validate(HeaderPolicy policy) {
        if (policy == null) {
            return;
        }
        policy.setHeaders().keySet().forEach(header -> doValidate(header, denySet));
        policy.removeHeaders().forEach(header -> doValidate(header, denyRemove));
    }

    private void doValidate(String header, Set<String> deny) {
        String normalized = HeaderNameRules.normalize(header);
        boolean admitted = extra.contains(normalized)
                || normalized.startsWith(HeaderPolicy.GATEWAY_PREFIX);
        if (RequestHeader.fromName(header).isPresent() || !admitted || deny.contains(normalized)) {
            throw DomainException.invalidObjectField();
        }
    }
}
