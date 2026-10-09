package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.error.FrameworkException;
import com.cloud.gateway.domain.capability.CapabilityConfigRule;
import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;
import com.cloud.gateway.domain.capability.header.HeaderPolicyComposer;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitPolicyComposer;
import com.cloud.gateway.domain.capability.resilience.TimeoutPolicyComposer;
import java.util.concurrent.atomic.AtomicInteger;
import com.cloud.gateway.domain.capability.policy.CapabilityBundle;
import com.cloud.gateway.domain.capability.policy.CapabilityBundleId;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyAssembler;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfigValidator;
import com.cloud.gateway.domain.capability.header.HeaderConfig;
import com.cloud.gateway.domain.capability.header.HeaderConfigRule;
import com.cloud.gateway.infrastructure.policy.CapabilityConfigCodecs;
import com.cloud.gateway.infrastructure.policy.CapabilityNodeStorageMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityCodecConfigurationTest {
    @Test
    void shouldReuseTheInjectedValidatorAcrossCodecsAndRequestAssembly() {
        AtomicInteger validations = new AtomicInteger();
        CapabilityPolicyConfigValidator validator = CapabilityPolicyConfigValidator.forComposers(List.of(
                HeaderPolicyComposer.INSTANCE,
                RateLimitPolicyComposer.INSTANCE,
                TimeoutPolicyComposer.INSTANCE),
                List.of(new CapabilityConfigRule<HeaderConfig>() {
                    public CapabilityId id() {
                        return BuiltInCapabilityCatalog.HEADER_APPLY;
                    }
                    public Class<HeaderConfig> configType() { return HeaderConfig.class; }
                    public void validate(List<HeaderConfig> configs) { validations.incrementAndGet(); }
                }), Set.of(BuiltInCapabilityCatalog.RATE_LIMIT));
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CapabilityPolicyConfigValidator.class, () -> validator);
            context.registerBean(CapabilityNodeStorageMapper.class, AbstractCapabilityNodeStorageMapStructMapperImpl::new);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(CapabilityCodecConfiguration.class);
            context.refresh();
            assertSame(validator, context.getBean(CapabilityPolicyConfigValidator.class));
            assertEquals(1, context.getBeansOfType(CapabilityPolicyConfigValidator.class).size());
            CapabilityPolicyConfig policyConfig = CapabilityPolicyConfig.empty();
            CapabilityConfigCodecs codecs = context.getBean(CapabilityConfigCodecs.class);
            codecs.validateSource(policyConfig);
            context.getBean(CapabilityPolicyAssembler.class).assemble(policyConfig, null, null);
            codecs.validateTarget(policyConfig);
            assertEquals(1, validations.get());
        }
    }

    @Test
    void shouldApplyTheSameDeploymentRulesThroughAssemblerAndCodecs() {
        for (boolean allow : List.of(true, false)) {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.registerBean(HeaderConfigRule.class, () -> new HeaderConfigRule(
                        allow ? Set.of() : Set.of("X-Deployment"), Set.of()));
                context.registerBean(CapabilityNodeStorageMapper.class, AbstractCapabilityNodeStorageMapStructMapperImpl::new);
                context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
                context.register(CapabilityCodecConfiguration.class);
                context.refresh();
                CapabilityPolicyAssembler assembler = context.getBean(CapabilityPolicyAssembler.class);
                CapabilityPolicyConfigValidator validator = context.getBean(CapabilityPolicyConfigValidator.class);
                CapabilityConfigCodecs codecs = context.getBean(CapabilityConfigCodecs.class);
                CapabilityBundleId id = new CapabilityBundleId("unbound");
                CapabilityPolicyConfig policyConfig = new CapabilityPolicyConfig(java.util.List.of(new CapabilityBundle(id,
                        List.of(new HeaderConfig(Set.of(), Map.of("X-Deployment", "value"), Set.of(), null)))), List.of());
                assertEquals(1, context.getBeansOfType(CapabilityPolicyAssembler.class).size());
                assertEquals(1, context.getBeansOfType(CapabilityPolicyConfigValidator.class).size());
                if (allow) {
                    assertDoesNotThrow(() -> validator.validate(policyConfig));
                    assertDoesNotThrow(() -> assembler.assemble(policyConfig, null, null));
                    assertDoesNotThrow(() -> codecs.validateSource(policyConfig));
                    assertDoesNotThrow(() -> codecs.validateTarget(policyConfig));
                } else {
                    assertThrows(DomainException.class, () -> validator.validate(policyConfig));
                    assertThrows(DomainException.class, () -> assembler.assemble(policyConfig, null, null));
                    assertThrows(DomainException.class, () -> codecs.validateSource(policyConfig));
                    assertThrows(DomainException.class, () -> codecs.validateTarget(policyConfig));
                }
            }
        }
    }
}
