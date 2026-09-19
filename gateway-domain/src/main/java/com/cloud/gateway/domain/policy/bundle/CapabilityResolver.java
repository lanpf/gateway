package com.cloud.gateway.domain.policy.bundle;

import com.cloud.framework.core.SubjectType;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.policy.capability.*;
import com.cloud.gateway.domain.policy.header.HeaderPolicy;
import com.cloud.gateway.domain.policy.execution.CapabilityContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resolves the composition against a request; two slots share one binding-selection rule set. */
public final class CapabilityResolver {

    /** Resolve both slots using only trusted facts; declared identity is never a binding fact. */
    public ResolvedCapabilities resolve(CapabilityComposition composition, CapabilityContext context) {
        Require.notNull(context, DomainException::missingField);
        return resolve(composition, context.endpoint(),
                context.granted() == null ? null : context.granted().app().appId(),
                context.granted() == null || context.granted().client() == null
                        ? null : context.granted().client().clientId(),
                context.granted() == null || context.granted().subject() == null
                        ? null : context.granted().subject().subjectType());
    }

    /**
     * Bindings are walked from least to most specific, so a single-valued node ends up holding
     * the most specific match's value while rate-limit groups accumulate — one per bundle. Header
     * defaults are substituted here, which is what lets {@link ResolvedCapabilities} assert its invariant.
     */
    private ResolvedCapabilities resolve(CapabilityComposition composition, Endpoint endpoint,
            AppId app, ClientId verifiedClient, SubjectType subject) {
        Require.notNull(composition, DomainException::missingField);
        List<RateLimitRules> rates = new ArrayList<>();
        Set<CapabilityBundleId> appliedRateBundles = new HashSet<>();
        TimeoutPolicy timeout = null;
        CircuitBreakerPolicy breaker = null;
        CachePolicy cache = null;
        HeaderPolicy headers = null;
        for (CapabilityBinding binding : matchedBindings(composition, endpoint, app, verifiedClient, subject)) {
            CapabilityBundle bundle = composition.bundles().get(binding.bundleId());
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
        return new ResolvedCapabilities(rates, timeout, breaker, cache,
                headers == null ? HeaderPolicy.defaults(null) : headers);
    }

    /** One binding-selection rule set shared by both slots; null identity excludes conditioned bindings. */
    private List<CapabilityBinding> matchedBindings(CapabilityComposition config, Endpoint endpoint,
            AppId app, ClientId verifiedClient, SubjectType subject) {
        return config.bindings().stream()
                .filter(binding -> endpoint == null
                        ? binding.serviceId() == null && binding.endpointId() == null && binding.tags().isEmpty()
                            && binding.appId() == null && binding.verifiedClientId() == null && binding.subjectType() == null
                        : binding.matches(endpoint, app, verifiedClient, subject))
                .sorted(Comparator.comparingInt(CapabilityBinding::specificity)
                        .thenComparing(binding -> binding.bindingId().value())).toList();
    }
}
