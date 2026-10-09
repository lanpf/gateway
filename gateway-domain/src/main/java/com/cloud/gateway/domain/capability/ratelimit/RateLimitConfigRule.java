package com.cloud.gateway.domain.capability.ratelimit;

import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.capability.CapabilityConfigRule;
import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Rule identities are globally unique; only EXACT configurations are supported. */
public final class RateLimitConfigRule implements CapabilityConfigRule<RateLimitConfig> {
    @Override
    public CapabilityId id() { return BuiltInCapabilityCatalog.RATE_LIMIT; }

    @Override
    public Class<RateLimitConfig> configType() { return RateLimitConfig.class; }

    @Override
    public void validate(List<RateLimitConfig> configs) {
        Set<RateLimitRuleId> noDuplicates = new HashSet<>();

        Require.notNull(configs);
        NullSafe.forEach(configs, config -> {
            NullSafe.forEach(config.rules(), rule -> {
                if (rule.mode() != RateLimitMode.EXACT) {
                    throw new DomainException(DomainError.ACCESS_SNAPSHOT_POLICY_INVALID);
                }
                if (!noDuplicates.add(rule.ruleId())) {
                    throw new DomainException(DomainError.ACCESS_SNAPSHOT_POLICY_INVALID);
                }
            });
        });
    }
}
