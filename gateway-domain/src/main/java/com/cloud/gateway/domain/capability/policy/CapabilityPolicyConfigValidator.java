package com.cloud.gateway.domain.capability.policy;

import com.cloud.framework.core.error.FrameworkException;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.capability.CapabilityConfig;
import com.cloud.gateway.domain.capability.CapabilityConfigRule;
import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates complete capability policy configuration; rules receive their own typed configurations. */
public final class CapabilityPolicyConfigValidator {
    private final Map<CapabilityId, Class<? extends CapabilityConfig>> configTypes;
    private final List<CapabilityConfigRule<?>> configRules;
    private final Set<CapabilityId> accumulatingCapabilities;

    private volatile WeakReference<CapabilityPolicyConfig> validatedPolicyConfig = new WeakReference<>(null);

    public CapabilityPolicyConfigValidator(
            Map<CapabilityId, Class<? extends CapabilityConfig>> configTypes,
            List<CapabilityConfigRule<?>> configRules,
            Set<CapabilityId> accumulatingCapabilities) {

        this.configTypes = NullSafe.copyOf(configTypes, (id, type) -> {
            if (!CapabilityConfig.class.isAssignableFrom(type)) {
                throw FrameworkException.invalidObject();
            }
        });

        Require.noDuplicates(configRules, CapabilityConfigRule::id);
        this.configRules = NullSafe.copyOf(configRules, rule -> {
            if (this.configTypes.get(rule.id()) != rule.configType()) {
                throw FrameworkException.invalidObject();
            }
        });

        this.accumulatingCapabilities = NullSafe.copyOf(accumulatingCapabilities, id -> {
            if (!this.configTypes.containsKey(id)) {
                throw FrameworkException.invalidObject();
            }
        });
    }


    /** Derives validation metadata from the same composers used for request policy assembly. */
    public static CapabilityPolicyConfigValidator forComposers(
            Collection<? extends CapabilityPolicyComposer<?, ?>> composers,
            List<CapabilityConfigRule<?>> configRules,
            Set<CapabilityId> accumulatingCapabilities) {
        NullSafe.forEach(composers, composer -> {
            if (composer.id() == null || composer.configType() == null || composer.policyType() == null) {
                throw FrameworkException.invalidObject();
            }
        });
        Require.noDuplicates(composers, CapabilityPolicyComposer::id);

        return new CapabilityPolicyConfigValidator(
                composers.stream().collect(Collectors.toMap(CapabilityPolicyComposer::id, CapabilityPolicyComposer::configType)),
                configRules,
                accumulatingCapabilities);
    }

    public Set<CapabilityId> capabilities() {
        return configTypes.keySet();
    }

    /** Exact registration compatibility; subtype acceptance applies only to configuration values. */
    public boolean supports(CapabilityId id, Class<? extends CapabilityConfig> configType) {
        Require.allNotNull(id, configType);
        return configTypes.get(id) == configType;
    }

    /**
     * Remembers the most recently validated immutable instance, without content hashing or locking.
     * Concurrent misses may repeat pure validation; only success is remembered under these fixed rules.
     */
    public void validate(CapabilityPolicyConfig policyConfig) {
        Require.notNull(policyConfig);
        if (validatedPolicyConfig.get() == policyConfig) {
            return;
        }
        policyConfig.getBundles().values().forEach(bundle -> bundle.getConfigs().forEach((id, config) -> {
            if (!id.equals(config.id())) {
                throw FrameworkException.invalidObject();
            }
            Class<? extends CapabilityConfig> type = configTypes.get(id);
            if (type == null) { throw new DomainException(DomainError.ACCESS_SNAPSHOT_POLICY_INVALID); }
            if (!type.isInstance(config)) {
                throw FrameworkException.invalidObject();
            }
        }));
        configRules.forEach(rule -> validateConfigs(rule, policyConfig));
        validatedPolicyConfig = new WeakReference<>(policyConfig);
    }

    private <C extends CapabilityConfig> void validateConfigs(
            CapabilityConfigRule<C> rule, CapabilityPolicyConfig policyConfig) {
        List<C> configs = policyConfig.getBundles().values().stream()
                .map(bundle -> bundle.getConfigs().get(rule.id()))
                .filter(Objects::nonNull)
                .map(rule.configType()::cast).toList();
        rule.validate(configs);
    }

    /**
     * Validates the complete policy configuration for publication: capability configurations and
     * cross-configuration rules, binding references against registries, and same-priority competition
     * between distinct bundles. The validator owns these rules; the
     * policy configuration itself stays a pure data part of the snapshot.
     */
    public void validate(CapabilityPolicyConfig policyConfig,
                         AppRegistry apps,
                         ClientRegistry clients,
                         EndpointRegistry endpoints) {
        Require.allNotNull(policyConfig, apps, clients, endpoints);
        validate(policyConfig);
        for (CapabilityBinding binding : policyConfig.getBindings().values()) {
            if (binding.appId() != null && apps.find(binding.appId()).isEmpty()
                    || binding.verifiedClientId() != null && clients.find(binding.verifiedClientId()).isEmpty()
                    || endpoints.values().stream().noneMatch(binding::matchesEndpoint)) {
                throw new DomainException(DomainError.ACCESS_SNAPSHOT_INVALID);
            }
        }
        List<CapabilityBinding> candidates = List.copyOf(policyConfig.getBindings().values());
        for (int i = 0; i < candidates.size(); i++) {
            CapabilityBinding left = candidates.get(i);
            for (int j = i + 1; j < candidates.size(); j++) {
                CapabilityBinding right = candidates.get(j);
                // Two bindings naming the same bundle are the same configuration source reached by two
                // routes: whichever wins, the request resolves to identical nodes, so this is idempotent
                // rather than ambiguous. Only distinct bundles competing for the same overridden node
                // leave the outcome dependent on which one wins. Rate limits already accumulate per
                // bundle, so excluding the same bundle keeps the two halves of the rule consistent.
                if (!left.bundleId().equals(right.bundleId())
                        && left.specificity() == right.specificity()
                        && left.overlaps(right)
                        && compete(policyConfig.getBundles().get(left.bundleId()), policyConfig.getBundles().get(right.bundleId()))
                        && endpoints.values().stream()
                            .anyMatch(endpoint -> left.matchesEndpoint(endpoint) && right.matchesEndpoint(endpoint))) {
                    throw new DomainException(DomainError.ACCESS_SNAPSHOT_INVALID);
                }
            }
        }
    }

    public boolean compete(CapabilityBundle left, CapabilityBundle right) {
        return configTypes.keySet().stream().anyMatch(id ->
                !accumulatingCapabilities.contains(id) && left.getConfigs().containsKey(id) && right.getConfigs().containsKey(id));
    }

}
