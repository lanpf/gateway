package com.cloud.gateway.domain.capability.policy;

import com.cloud.framework.core.SubjectType;
import com.cloud.framework.core.error.FrameworkException;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.capability.CapabilityConfig;
import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Matches bindings and composes request policies after content validation. */
public final class CapabilityPolicyAssembler {
    private final Map<CapabilityId, CapabilityPolicyComposer<?, ?>> composers;
    private final CapabilityPolicyConfigValidator validator;

    public CapabilityPolicyAssembler(
            Collection<? extends CapabilityPolicyComposer<?, ?>> composers,
            CapabilityPolicyConfigValidator validator) {
        Require.noDuplicates(composers, CapabilityPolicyComposer::policyType);
        this.composers = Map.copyOf(Require.noDuplicates(composers, CapabilityPolicyComposer::id));
        this.validator = Require.notNull(validator);

        if (!this.composers.keySet().equals(validator.capabilities())
                || this.composers.values().stream().anyMatch(composer ->
                    !validator.supports(composer.id(), composer.configType()))) {
            throw FrameworkException.invalidObject();
        }
    }

    public Set<CapabilityId> capabilities() {
        return composers.keySet();
    }

    /** Assemble using only trusted facts; declared identity is never a binding fact. */
    public EffectiveCapabilityPolicies assemble(
            CapabilityPolicyConfig policyConfig,
            Endpoint endpoint,
            AccessGrantedContext granted) {
        return doAssemble(
                policyConfig,
                endpoint,
                granted == null ? null : granted.app().appId(),
                granted == null || granted.client() == null ? null : granted.client().clientId(),
                granted == null || granted.subject() == null ? null : granted.subject().subjectType());
    }

    /**
     * Bindings are walked from least to most specific; composers own merge and default rules.
     * Each bundle appears once, at its most specific matching position.
     */
    private EffectiveCapabilityPolicies doAssemble(
            CapabilityPolicyConfig policyConfig,
            Endpoint endpoint,
            AppId app,
            ClientId verifiedClient,
            SubjectType subject) {
        validator.validate(policyConfig);
        LinkedHashMap<CapabilityBundleId, CapabilityBundle> selected = new LinkedHashMap<>();
        for (CapabilityBinding binding : matchedBindings(policyConfig, endpoint, app, verifiedClient, subject)) {
            // A repeated bundle takes its most specific position for replacement nodes.
            selected.remove(binding.bundleId());
            selected.put(binding.bundleId(), policyConfig.getBundles().get(binding.bundleId()));
        }
        List<CapabilityBundle> bundles = List.copyOf(selected.values());
        List<EffectiveCapabilityPolicies.Value<?>> policies = composers.values().stream()
                .<EffectiveCapabilityPolicies.Value<?>>map(composer -> compose(composer, bundles)).toList();
        return new EffectiveCapabilityPolicies(policies);
    }

    private <C extends CapabilityConfig, P> EffectiveCapabilityPolicies.Value<P> compose(
            CapabilityPolicyComposer<C, P> composer, List<CapabilityBundle> bundles) {
        List<C> configs = bundles.stream()
                .flatMap(bundle -> bundle.find(composer.id(), composer.configType()).stream()).toList();
        return new EffectiveCapabilityPolicies.Value<>(composer.policyType(), composer.compose(configs));
    }

    /** One binding-selection rule set independent of slots; null identity excludes conditioned bindings. */
    private List<CapabilityBinding> matchedBindings(CapabilityPolicyConfig policyConfig, Endpoint endpoint,
            AppId app, ClientId verifiedClient, SubjectType subject) {
        return policyConfig.getBindings().values().stream()
                .filter(binding -> endpoint == null
                        ? binding.serviceId() == null && binding.endpointId() == null && binding.tags().isEmpty()
                            && binding.appId() == null && binding.verifiedClientId() == null && binding.subjectType() == null
                        : binding.matches(endpoint, app, verifiedClient, subject))
                .sorted(Comparator.comparingInt(CapabilityBinding::specificity)
                        .thenComparing(binding -> binding.bindingId().value()))
                .toList();
    }
}
