package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyAssembler;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfigValidator;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitPolicyComposer;
import com.cloud.gateway.domain.capability.resilience.TimeoutPolicyComposer;
import com.cloud.gateway.domain.capability.header.HeaderPolicyComposer;
import com.cloud.gateway.domain.capability.header.HeaderConfigRule;
import com.cloud.gateway.infrastructure.policy.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/** Fixed catalog: new policyConfig register policy composers and codecs, never payload class names. */
@Configuration(proxyBeanMethods = false)
public class CapabilityCodecConfiguration {
    @Bean @ConditionalOnMissingBean
    CapabilityPolicyConfigValidator capabilityPolicyConfigValidator(HeaderConfigRule headerRules) {
        return BuiltInCapabilityCatalog.validator(headerRules);
    }

    @Bean @ConditionalOnMissingBean
    CapabilityPolicyAssembler capabilityPolicyAssembler(CapabilityPolicyConfigValidator validator) {
        return BuiltInCapabilityCatalog.assembler(validator);
    }

    @Bean
    CapabilityConfigCodecs capabilityConfigCodecs(CapabilityPolicyConfigValidator validator,
            CapabilityNodeStorageMapper mapper, ObjectMapper json) {
        return builtIns(validator, mapper, json);
    }

    public static CapabilityConfigCodecs builtIns(CapabilityPolicyConfigValidator validator,
            CapabilityNodeStorageMapper mapper, ObjectMapper json) {
        return new CapabilityConfigCodecs(validator, List.of(
                new CapabilityConfigCodec<>(RateLimitPolicyComposer.INSTANCE,
                        StoredCapabilityPolicyConfig.RateLimit.class, mapper::toDomain, mapper::toStored),
                new CapabilityConfigCodec<>(TimeoutPolicyComposer.INSTANCE,
                        StoredCapabilityPolicyConfig.Timeout.class, mapper::toDomain, mapper::toStored),
                new CapabilityConfigCodec<>(HeaderPolicyComposer.INSTANCE,
                        StoredCapabilityPolicyConfig.Headers.class, mapper::toDomain, mapper::toStored)), json);
    }
}
