package com.cloud.gateway.domain.capability.catalog;

import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.header.HeaderConfigRule;
import com.cloud.gateway.domain.capability.header.HeaderPolicyComposer;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyAssembler;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyComposer;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfigValidator;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitConfigRule;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitPolicyComposer;
import com.cloud.gateway.domain.capability.resilience.TimeoutPolicyComposer;

import java.util.List;
import java.util.Set;

/**
 * The code-owned catalog of built-in policyConfig and factories for policy assembly and validation:
 * the three built-in composers, the accumulation declaration
 * (rate limit) and the policy configuration rules, plus the deployment-owned header rules.
 *
 * <p>Which policyConfig exist is a deployment/code property, not a snapshot-config property: a
 * policy configuration can only use nodes registered here, and unknown nodes are rejected. Configuration
 * drives <em>usage</em> (which bundle binds which capability node), never the catalog itself.
 * Adding a capability means adding its composer, rules and runtime contract in code and registering
 * it here; no dynamic discovery. Generic containers never depend on this list.
 */
public final class BuiltInCapabilityCatalog {
    public static final CapabilityId HEADER_APPLY = new CapabilityId("header-apply");
    public static final CapabilityId RATE_LIMIT = new CapabilityId("rate-limit");
    public static final CapabilityId TIMEOUT = new CapabilityId("timeout");
    private static final List<CapabilityPolicyComposer<?, ?>> COMPOSERS = List.of(
            RateLimitPolicyComposer.INSTANCE, TimeoutPolicyComposer.INSTANCE, HeaderPolicyComposer.INSTANCE);

    private BuiltInCapabilityCatalog() { }

    public static CapabilityPolicyAssembler assembler() {
        return assembler(HeaderConfigRule.empty());
    }

    public static CapabilityPolicyAssembler assembler(HeaderConfigRule headerRules) {
        return assembler(validator(headerRules));
    }

    public static CapabilityPolicyAssembler assembler(CapabilityPolicyConfigValidator validator) {
        return new CapabilityPolicyAssembler(COMPOSERS, validator);
    }

    public static CapabilityPolicyConfigValidator validator() {
        return validator(HeaderConfigRule.empty());
    }

    public static CapabilityPolicyConfigValidator validator(HeaderConfigRule headerRules) {
        return CapabilityPolicyConfigValidator.forComposers(COMPOSERS,
                List.of(new RateLimitConfigRule(), headerRules), Set.of(RATE_LIMIT));
    }
}
