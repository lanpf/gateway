package com.cloud.gateway.domain.policy.bundle;

import com.cloud.framework.core.SubjectType;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.policy.capability.*;
import com.cloud.gateway.domain.policy.header.HeaderPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resolves the composition against a request; two slots share one binding-selection rule set. */
public final class PolicyResolver {

    /**
     * Prepare-stage slot: rate-limit groups of global and identity-free bindings only.
     * A null endpoint (unknown or ambiguous path) contributes global bindings exclusively.
     */
    public List<RateLimitRules> preAuthRules(PolicyConfig config, Endpoint endpoint) {
        List<RateLimitRules> rates = new ArrayList<>();
        Set<PolicyBundleId> appliedBundles = new HashSet<>();
        for (PolicyBinding binding : matchedBindings(config, endpoint, null, null, null)) {
            PolicyBundle bundle = config.bundles().get(binding.bundleId());
            if (bundle.rateLimit() != null && appliedBundles.add(bundle.bundleId())) {
                rates.add(bundle.rateLimit());
            }
        }
        return List.copyOf(rates);
    }

    /**
     * Admit-stage slot: full effective policy for an authorized request. The endpoint is the
     * unique match; verifiedClient and subject stay null when that identity is absent.
     * Header defaults are applied here: credential-forwarding endpoint defaults no longer exist.
     */
    public EffectivePolicy resolve(PolicyConfig config, Endpoint endpoint,
            AppId app, ClientId verifiedClient, SubjectType subject) {
        Require.notNull(endpoint, DomainException::missingField);
        EffectivePolicy selected = resolveNodes(config, endpoint, app, verifiedClient, subject);
        HeaderPolicy headers = selected.headers() == null
                ? HeaderPolicy.defaults(null) : selected.headers();
        return new EffectivePolicy(selected.rateLimits(), selected.timeout(), selected.circuitBreaker(),
                selected.cache(), headers);
    }

    /** One binding-selection rule set shared by both slots; null identity excludes conditioned bindings. */
    private List<PolicyBinding> matchedBindings(PolicyConfig config, Endpoint endpoint,
            AppId app, ClientId verifiedClient, SubjectType subject) {
        return config.bindings().stream()
                .filter(binding -> endpoint == null
                        ? binding.serviceId() == null && binding.endpointId() == null && binding.tags().isEmpty()
                            && binding.appId() == null && binding.verifiedClientId() == null && binding.subjectType() == null
                        : binding.matches(endpoint, app, verifiedClient, subject))
                .sorted(Comparator.comparingInt(PolicyBinding::specificity)
                        .thenComparing(binding -> binding.bindingId().value())).toList();
    }

    private EffectivePolicy resolveNodes(PolicyConfig config, Endpoint endpoint,
            AppId app, ClientId verifiedClient, SubjectType subject) {
        List<RateLimitRules> rates = new ArrayList<>();
        TimeoutPolicy timeout = null;
        CircuitBreakerPolicy breaker = null;
        CachePolicy cache = null;
        HeaderPolicy headers = null;
        Set<PolicyBundleId> appliedRateBundles = new HashSet<>();
        for (PolicyBinding binding : matchedBindings(config, endpoint, app, verifiedClient, subject)) {
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
