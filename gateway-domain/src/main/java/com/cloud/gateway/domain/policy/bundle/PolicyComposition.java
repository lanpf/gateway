package com.cloud.gateway.domain.policy.bundle;

import com.cloud.framework.core.RequestHeader;
import com.cloud.framework.core.HeaderType;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.policy.capability.RateLimitMode;
import com.cloud.gateway.domain.policy.capability.RateLimitPolicy;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public record PolicyComposition(Map<PolicyBundleId, PolicyBundle> bundles, List<PolicyBinding> bindings) {
    public PolicyComposition {
        if (bundles != null) {
            bundles.forEach((id, bundle) -> {
                Require.notNull(id, DomainException::missingField);
                Require.notNull(bundle, DomainException::missingField);
            });
        }
        if (bindings != null) {
            bindings.forEach(binding -> Require.notNull(binding, DomainException::missingField));
        }
        bundles = Map.copyOf(bundles == null ? Map.of() : bundles);
        bindings = List.copyOf(bindings == null ? List.of() : bindings);
        bundles.forEach((id, bundle) -> {
            if (!id.equals(bundle.bundleId())) {
                throw DomainException.invalidField();
            }
        });
        HashSet<PolicyBindingId> ids = new HashSet<>();
        HashSet<String> ruleIds = new HashSet<>();
        for (PolicyBundle bundle : bundles.values()) {
            if (bundle.rateLimit() != null) {
                for (RateLimitPolicy rule : bundle.rateLimit().rules()) {
                    if (!ruleIds.add(rule.ruleId())
                            || rule.mode() != RateLimitMode.EXACT) {
                        throw DomainException.invalidField();
                    }
                }
            }
        }
        for (PolicyBinding binding : bindings) {
            if (!ids.add(binding.bindingId()) || !bundles.containsKey(binding.bundleId())) {
                throw DomainException.invalidField();
            }
        }
    }

    public static PolicyComposition empty() { return new PolicyComposition(Map.of(), List.of()); }

    public void validate(IngressAccessContent content) {
        for (PolicyBinding binding : bindings) {
            if (binding.appId() != null && !content.appPolicies().containsKey(binding.appId())
                    || binding.verifiedClientId() != null && !content.clients().containsKey(binding.verifiedClientId())
                    || content.endpointRegistry().definitions().stream().noneMatch(binding::matchesEndpoint)) {
                throw DomainException.invalidField();
            }
        }
        for (int i = 0; i < bindings.size(); i++) {
            PolicyBinding left = bindings.get(i);
            for (int j = i + 1; j < bindings.size(); j++) {
                PolicyBinding right = bindings.get(j);
                if (left.specificity() == right.specificity() && left.overlapsContext(right)
                        && bundles.get(left.bundleId()).competesWith(bundles.get(right.bundleId()))
                        && content.endpointRegistry().definitions().stream()
                                .anyMatch(endpoint -> left.matchesEndpoint(endpoint) && right.matchesEndpoint(endpoint))) {
                    throw DomainException.invalidField();
                }
            }
        }
    }

    public Set<String> trustedHeaderNames() {
        HashSet<String> names = new HashSet<>();
        RequestHeader.fromType(HeaderType.CONTEXT)
                .forEach(header -> names.add(header.headerName().toLowerCase(Locale.ROOT)));
        return Set.copyOf(names);
    }
}
