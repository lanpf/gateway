package com.cloud.gateway.domain.policy.bundle;

import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.policy.capability.*;
import com.cloud.gateway.domain.policy.header.HeaderPolicy;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PolicyResolver {
    public EffectivePolicy resolve(PolicyComposition config, Endpoint endpoint,
            AppId app, ClientId verifiedClient, SubjectType subject, EndpointPolicy access) {
        EffectivePolicy selected = resolve(config, endpoint, app, verifiedClient, subject);
        HeaderPolicy headers = selected.headers() == null
                ? HeaderPolicy.defaults(access.credentialForwarding()) : selected.headers();
        return new EffectivePolicy(selected.rateLimits(), selected.timeout(), selected.circuitBreaker(),
                selected.cache(), headers);
    }

    public EffectivePolicy resolve(PolicyComposition config, Endpoint endpoint,
            AppId app, ClientId verifiedClient, SubjectType subject) {
        List<PolicyBinding> matched = config.bindings().stream()
                .filter(binding -> endpoint == null
                        ? binding.serviceId() == null && binding.endpointId() == null && binding.tags().isEmpty()
                            && binding.appId() == null && binding.verifiedClientId() == null && binding.subjectType() == null
                        : binding.matches(endpoint, app, verifiedClient, subject))
                .sorted(Comparator.comparingInt(PolicyBinding::specificity)
                        .thenComparing(binding -> binding.bindingId().value())).toList();
        ArrayList<RateLimitRules> rates = new ArrayList<>();
        TimeoutPolicy timeout = null;
        CircuitBreakerPolicy breaker = null;
        CachePolicy cache = null;
        HeaderPolicy headers = null;
        java.util.HashSet<PolicyBundleId> appliedRateBundles = new java.util.HashSet<>();
        for (PolicyBinding binding : matched) {
            PolicyBundle bundle = config.bundles().get(binding.bundleId());
            if (bundle.rateLimit() != null && appliedRateBundles.add(bundle.bundleId())) {
                rates.add(bundle.rateLimit());
            }
            if (bundle.timeout() != null) {
                timeout = bundle.timeout();
            }
            if (bundle.circuitBreaker() != null) {
                breaker = bundle.circuitBreaker();
            }
            if (bundle.cache() != null) {
                cache = bundle.cache();
            }
            if (bundle.headers() != null) {
                headers = bundle.headers();
            }
        }
        return new EffectivePolicy(rates, timeout, breaker, cache, headers);
    }
}
