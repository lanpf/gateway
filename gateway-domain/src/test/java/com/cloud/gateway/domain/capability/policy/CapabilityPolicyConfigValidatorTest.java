package com.cloud.gateway.domain.capability.policy;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.error.FrameworkException;
import com.cloud.gateway.domain.capability.CapabilityConfig;
import com.cloud.gateway.domain.capability.CapabilityConfigRule;
import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;
import com.cloud.gateway.domain.capability.header.HeaderConfig;
import com.cloud.gateway.domain.capability.header.HeaderConfigRule;
import com.cloud.gateway.domain.capability.header.HeaderPolicyComposer;
import com.cloud.gateway.domain.capability.ratelimit.*;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityPolicyConfigValidatorTest {
    @Test
    void shouldRememberOnlyTheMostRecentSuccessfulInstanceWithoutHashingContent() {
        AtomicInteger calls = new AtomicInteger();
        CapabilityId id = new CapabilityId("no-hash");
        CapabilityConfig config = new CapabilityConfig() {
            public CapabilityId id() { return id; }
            @Override public int hashCode() { throw new AssertionError("validation must not hash content"); }
        };
        CapabilityConfigRule<CapabilityConfig> rule = new CapabilityConfigRule<>() {
            public CapabilityId id() { return id; }
            public Class<CapabilityConfig> configType() { return CapabilityConfig.class; }
            public void validate(List<CapabilityConfig> configs) { calls.incrementAndGet(); }
        };
        CapabilityPolicyConfigValidator validator = new CapabilityPolicyConfigValidator(
                Map.of(id, CapabilityConfig.class), List.of(rule), Set.of());
        CapabilityPolicyConfig first = policyConfig(bundle("one", config));
        CapabilityPolicyConfig equal = policyConfig(bundle("one", config));
        assertEquals(first, equal);
        validator.validate(first);
        validator.validate(first);
        assertEquals(1, calls.get());
        validator.validate(equal);
        validator.validate(equal);
        assertEquals(2, calls.get());
        validator.validate(first);
        assertEquals(3, calls.get());
    }

    @Test
    void shouldRejectMissingInputsAndAcceptEmptyLists() {
        HeaderConfigRule headers = HeaderConfigRule.empty();
        RateLimitConfigRule limits = new RateLimitConfigRule();
        CapabilityPolicyConfigValidator validator = BuiltInCapabilityCatalog.validator(headers);
        assertThrows(FrameworkException.class, () -> headers.validate(null));
        assertThrows(FrameworkException.class, () -> limits.validate(null));
        assertThrows(FrameworkException.class, () -> headers.validate(Arrays.asList((HeaderConfig) null)));
        assertThrows(FrameworkException.class, () -> limits.validate(Arrays.asList((RateLimitConfig) null)));
        assertThrows(FrameworkException.class, () -> validator.validate(null));
        assertDoesNotThrow(() -> headers.validate(List.of()));
        assertDoesNotThrow(() -> limits.validate(List.of()));
        assertDoesNotThrow(() -> validator.validate(CapabilityPolicyConfig.empty()));
    }

    @Test
    void shouldRejectUnboundHeadersAndDuplicateRulesBeforeAssembly() {
        HeaderConfig header = new HeaderConfig(Set.of(), Map.of("X-Deployment", "value"), Set.of(), null);
        CapabilityPolicyConfig headers = policyConfig(bundle("unbound", header));
        HeaderConfigRule denied = new HeaderConfigRule(Set.of("X-Deployment"), Set.of());
        assertDoesNotThrow(() -> BuiltInCapabilityCatalog.validator().validate(headers));
        assertThrows(DomainException.class, () -> denied.validate(List.of(header)));
        assertThrows(DomainException.class, () -> BuiltInCapabilityCatalog.validator(denied).validate(headers));
        assertThrows(DomainException.class, () -> BuiltInCapabilityCatalog.assembler(denied).assemble(headers, null, null));
        RateLimitConfig limit = new RateLimitConfig(List.of(
                new RateLimitRule("shared", RateLimitDimension.IP, 10, 1, RateLimitMode.EXACT)), null);
        CapabilityPolicyConfig duplicate = policyConfig(bundle("first", limit), bundle("second", limit));
        assertThrows(DomainException.class, () -> new RateLimitConfigRule().validate(List.of(limit, limit)));
        assertThrows(DomainException.class, () -> BuiltInCapabilityCatalog.validator().validate(duplicate));
        assertThrows(DomainException.class, () -> BuiltInCapabilityCatalog.assembler().assemble(duplicate, null, null));
        assertDoesNotThrow(() -> new RateLimitConfigRule().validate(List.of(limit)));
    }

    @Test
    void shouldPassAllMatchingConfigsWithoutDeduplicatingOrExposingPolicyConfig() {
        HeaderConfig header = HeaderConfig.defaults();
        AtomicInteger calls = new AtomicInteger();
        List<CapabilityConfigRule<?>> rules = new ArrayList<>(List.of(headerRules(configs -> {
            assertEquals(2, configs.size());
            assertTrue(configs.stream().allMatch(header::equals));
            assertThrows(UnsupportedOperationException.class, configs::clear);
            calls.incrementAndGet();
        })));
        List<CapabilityPolicyComposer<?, ?>> composers = List.of(HeaderPolicyComposer.INSTANCE, RateLimitPolicyComposer.INSTANCE);
        CapabilityPolicyConfigValidator validator = CapabilityPolicyConfigValidator.forComposers(composers, rules, Set.of());
        CapabilityPolicyAssembler assembler = new CapabilityPolicyAssembler(composers, validator);
        rules.clear();
        CapabilityPolicyConfig policyConfig = policyConfig(bundle("first", header), bundle("second", header),
                bundle("other", RateLimitConfig.disabled()));
        validator.validate(policyConfig);
        assembler.assemble(policyConfig, null, null);
        assertEquals(1, calls.get());
    }

    @Test
    void shouldPassEmptyListWhenCapabilityIsAbsent() {
        AtomicInteger calls = new AtomicInteger();
        validator(List.of(headerRules(configs -> {
            assertTrue(configs.isEmpty());
            calls.incrementAndGet();
        }))).validate(CapabilityPolicyConfig.empty());
        assertEquals(1, calls.get());
    }

    @Test
    void shouldRejectUnknownAndWrongTypedConfigurationsBeforeRules() {
        AtomicInteger calls = new AtomicInteger();
        CapabilityPolicyConfigValidator validator = validator(List.of(headerRules(configs -> calls.incrementAndGet())));
        assertThrows(DomainException.class, () -> validator.validate(policyConfig(bundle("unknown", () -> new CapabilityId("unknown")))));
        assertThrows(FrameworkException.class, () -> validator.validate(policyConfig(bundle("wrong", () -> BuiltInCapabilityCatalog.HEADER_APPLY))));
        assertEquals(0, calls.get());
    }

    @Test
    void shouldRejectInvalidRuleRegistrations() {
        CapabilityConfigRule<HeaderConfig> rule = headerRules(configs -> {});
        assertThrows(FrameworkException.class, () -> validator(null));
        assertThrows(FrameworkException.class, () -> validator(Arrays.asList((CapabilityConfigRule<?>) null)));
        assertThrows(FrameworkException.class, () -> validator(List.of(rule, rule)));
        assertThrows(FrameworkException.class, () -> validator(List.of(new RateLimitConfigRule())));
        assertThrows(FrameworkException.class, () -> new CapabilityPolicyConfigValidator(
                Map.of(BuiltInCapabilityCatalog.HEADER_APPLY, RateLimitConfig.class), List.of(rule), Set.of()));
        assertThrows(FrameworkException.class, () -> new CapabilityPolicyConfigValidator(
                Map.of(), List.of(), Set.of(BuiltInCapabilityCatalog.HEADER_APPLY)));
    }

    @Test
    void shouldNotCacheFailuresOrShareCacheBetweenDifferentRules() {
        AtomicInteger calls = new AtomicInteger();
        CapabilityPolicyConfigValidator rejected = validator(List.of(headerRules(configs -> {
            calls.incrementAndGet();
            throw FrameworkException.invalidObject();
        })));
        CapabilityPolicyConfig policyConfig = CapabilityPolicyConfig.empty();
        validator(List.of()).validate(policyConfig);
        assertThrows(FrameworkException.class, () -> rejected.validate(policyConfig));
        assertThrows(FrameworkException.class, () -> rejected.validate(policyConfig));
        assertEquals(2, calls.get());
    }

    @Test
    void shouldStillCheckReferencesAfterContentWasCached() {
        CapabilityBundle bundle = bundle("headers", HeaderConfig.defaults());
        CapabilityPolicyConfig policyConfig = new CapabilityPolicyConfig(List.of(bundle), List.of(
                new CapabilityBinding(new CapabilityBindingId("global"), bundle.getBundleId(),
                        null, null, Set.of(), null, null, null)));
        CapabilityPolicyConfigValidator validator = validator(List.of());
        validator.validate(policyConfig);
        assertThrows(DomainException.class, () -> validator.validate(policyConfig,
                new AppRegistry(List.of()), new ClientRegistry(List.of()), new EndpointRegistry(List.of())));
    }

    private static CapabilityPolicyConfigValidator validator(List<CapabilityConfigRule<?>> rules) {
        return new CapabilityPolicyConfigValidator(Map.of(BuiltInCapabilityCatalog.HEADER_APPLY, HeaderConfig.class), rules, Set.of());
    }

    private static CapabilityConfigRule<HeaderConfig> headerRules(Consumer<List<HeaderConfig>> action) {
        return new CapabilityConfigRule<>() {
            public CapabilityId id() { return BuiltInCapabilityCatalog.HEADER_APPLY; }
            public Class<HeaderConfig> configType() { return HeaderConfig.class; }
            public void validate(List<HeaderConfig> configs) { action.accept(configs); }
        };
    }

    private static CapabilityBundle bundle(String id, CapabilityConfig config) {
        return new CapabilityBundle(new CapabilityBundleId(id), List.of(config));
    }

    private static CapabilityPolicyConfig policyConfig(CapabilityBundle... bundles) {
        return new CapabilityPolicyConfig(Arrays.asList(bundles), List.of());
    }
}
