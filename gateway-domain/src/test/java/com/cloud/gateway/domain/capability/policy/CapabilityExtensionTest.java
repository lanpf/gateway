package com.cloud.gateway.domain.capability.policy;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.capability.header.EffectiveHeaderPolicy;

import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;

import com.cloud.gateway.domain.capability.*;
import com.cloud.gateway.domain.capability.header.HeaderPolicyComposer;
import com.cloud.gateway.domain.capability.ratelimit.*;
import com.cloud.gateway.domain.capability.resilience.*;
import com.cloud.framework.core.error.FrameworkException;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.endpoint.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityExtensionTest {
    private static final CapabilityId ID = new CapabilityId("test-budget");
    private record Budget(int value) implements CapabilityConfig {
        @Override public CapabilityId id() { return ID; }
    }
    private static final CapabilityPolicyComposer<Budget, Budget> DEFINITION = new CapabilityPolicyComposer<>() {
        public CapabilityId id() { return ID; }
        public Class<Budget> configType() { return Budget.class; }
        public Class<Budget> policyType() { return Budget.class; }
        public Budget compose(List<Budget> orderedConfigs) { return orderedConfigs.isEmpty() ? new Budget(1) : orderedConfigs.get(orderedConfigs.size() - 1); }
    };

    private static List<CapabilityPolicyComposer<?, ?>> composers() {
        return List.of(RateLimitPolicyComposer.INSTANCE, TimeoutPolicyComposer.INSTANCE,
                HeaderPolicyComposer.INSTANCE, DEFINITION);
    }

    private static CapabilityPolicyConfigValidator validator() {
        return CapabilityPolicyConfigValidator.forComposers(composers(),
                List.of(new RateLimitConfigRule()), Set.of(BuiltInCapabilityCatalog.RATE_LIMIT));
    }

    private static CapabilityPolicyAssembler assembler() {
        return new CapabilityPolicyAssembler(composers(), validator());
    }

    @Test void shouldResolveAnAdditionalCapabilityWithoutChangingContainersOrAssembler() {
        CapabilityBundle global = bundle("global", new Budget(2));
        CapabilityBundle specific = bundle("specific", new Budget(9));
        CapabilityPolicyConfig policyConfig = new CapabilityPolicyConfig(java.util.List.of(global, specific),
                List.of(binding("global", global, null), binding("endpoint", specific, endpoint().getId())));
        validator().validate(policyConfig, new AppRegistry(List.of()), new ClientRegistry(List.of()),
                new EndpointRegistry(List.of(endpoint())));
        EffectiveCapabilityPolicies effective = assembler().assemble(policyConfig, endpoint(), null);
        assertEquals(new Budget(9), effective.get(Budget.class));
        assertNotNull(effective.get(EffectiveHeaderPolicy.class));
        assertFalse(effective.get(EffectiveTimeoutPolicy.class).configured());
    }

    @Test void shouldRejectUnknownCapabilityAndSpoofedConfigurationType() {
        CapabilityBundle bundle = bundle("unknown", new Budget(2));
        assertThrows(DomainException.class, () -> CapabilityPolicyConfigValidator.forComposers(List.of(), List.of(), Set.of())
                .validate(policyConfig(bundle)));
        CapabilityConfig forged = () -> ID;
        assertThrows(FrameworkException.class, () -> validator().validate(policyConfig(bundle("forged", forged))));
        assertThrows(DomainException.class, () -> new CapabilityBundle(bundle.getBundleId(),
                List.of(new Budget(1), new Budget(2))));
        assertThrows(FrameworkException.class, () -> new CapabilityPolicyAssembler(List.of(DEFINITION, DEFINITION),
                CapabilityPolicyConfigValidator.forComposers(List.of(DEFINITION), List.of(), Set.of())));
    }

    @Test void shouldReadByPolicyTypeWithoutRequiringTheProducingComposer() {
        EffectiveCapabilityPolicies effective = assembler()
                .assemble(CapabilityPolicyConfig.empty(), null, null);
        assertThrows(FrameworkException.class, () -> effective.get(String.class));
        assertNotNull(effective.get(new HeaderPolicyComposer().policyType()));
        assertThrows(FrameworkException.class, () -> CapabilityConfig.requireType(RateLimitPolicyComposer.INSTANCE.id(), RateLimitPolicyComposer.INSTANCE.configType(), (CapabilityConfig) () -> BuiltInCapabilityCatalog.RATE_LIMIT));
        assertThrows(FrameworkException.class, () -> CapabilityConfig.requireType(HeaderPolicyComposer.INSTANCE.id(), HeaderPolicyComposer.INSTANCE.configType(), null));
    }

    @Test void shouldDelegateSamePriorityConflictsToTheNewCapability() {
        CapabilityBundle first = bundle("first", new Budget(1));
        CapabilityBundle second = bundle("second", new Budget(2));
        CapabilityPolicyConfig policyConfig = new CapabilityPolicyConfig(java.util.List.of(first, second),
                List.of(binding("one", first, null), binding("two", second, null)));
        assertThrows(DomainException.class, () -> validator().validate(policyConfig, new AppRegistry(List.of()),
                new ClientRegistry(List.of()), new EndpointRegistry(List.of(endpoint()))));
    }

    @Test void shouldRejectDifferentCapabilitiesSharingTheSamePolicyTypeAtRegistration() {
        CapabilityPolicyComposer<Budget, Budget> duplicateType = new CapabilityPolicyComposer<>() {
            public CapabilityId id() { return new CapabilityId("another-budget"); }
            public Class<Budget> configType() { return Budget.class; }
            public Class<Budget> policyType() { return Budget.class; }
            public Budget compose(List<Budget> configs) { return new Budget(1); }
        };
        assertThrows(FrameworkException.class,
                () -> new CapabilityPolicyAssembler(List.of(DEFINITION, duplicateType),
                        CapabilityPolicyConfigValidator.forComposers(List.of(DEFINITION, duplicateType), List.of(), Set.of())));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldRejectWrongTypedAndMissingComposerOutputs() {
        for (Object output : Arrays.asList(null, "wrong-type")) {
            CapabilityPolicyComposer invalid = new CapabilityPolicyComposer() {
                public CapabilityId id() { return ID; }
                public Class configType() { return Budget.class; }
                public Class policyType() { return Budget.class; }
                public Object compose(List configs) { return output; }
            };
            List<CapabilityPolicyComposer<?, ?>> composers = List.of((CapabilityPolicyComposer<?, ?>) invalid);
            CapabilityPolicyConfigValidator validator = CapabilityPolicyConfigValidator.forComposers(composers, List.of(), Set.of());
            CapabilityPolicyAssembler assembler = new CapabilityPolicyAssembler(composers, validator);
            assertThrows(FrameworkException.class,
                    () -> assembler.assemble(CapabilityPolicyConfig.empty(), null, null));
        }
    }

    @Test void shouldKeepTheMostSpecificOccurrenceOfARepeatedBundle() {
        CapabilityBundle first = bundle("first", new Budget(1));
        CapabilityBundle second = bundle("second", new Budget(2));
        CapabilityPolicyConfig policyConfig = new CapabilityPolicyConfig(java.util.List.of(first, second),
                List.of(binding("global", first, null),
                        new CapabilityBinding(new CapabilityBindingId("service"), second.getBundleId(),
                                endpoint().getServiceId(), null, Set.of(), null, null, null),
                        binding("endpoint", first, endpoint().getId())));
        assertEquals(new Budget(1), assembler()
                .assemble(policyConfig, endpoint(), null).get(Budget.class));
    }

    @Test void shouldValidateRuleIdsAcrossAllBundles() {
        RateLimitConfig policy = new RateLimitConfig(List.of(
                new RateLimitRule("shared", RateLimitDimension.IP, 10, 1, RateLimitMode.EXACT)), null);
        assertThrows(DomainException.class, () -> validator().validate(policyConfig(
                bundle("one", policy), bundle("two", policy))));
    }

    @Test void shouldValidateUnmatchedBundlesBeforeAssemblingPolicies() {
        CapabilityBundle invalid = bundle("unmatched", (CapabilityConfig) () -> ID);
        CapabilityPolicyConfig policyConfig = new CapabilityPolicyConfig(java.util.List.of(invalid), List.of());
        assertThrows(FrameworkException.class, () -> assembler()
                .assemble(policyConfig, null, null));
    }

    @Test void shouldRejectDuplicateRulesEvenWhenNeitherBundleIsBound() {
        RateLimitConfig limit = new RateLimitConfig(List.of(
                new RateLimitRule("duplicate", RateLimitDimension.IP, 10, 1, RateLimitMode.EXACT)), null);
        CapabilityBundle first = bundle("first", limit);
        CapabilityBundle second = bundle("second", limit);
        CapabilityPolicyConfig policyConfig = new CapabilityPolicyConfig(java.util.List.of(first, second), List.of());
        assertThrows(DomainException.class, () -> assembler()
                .assemble(policyConfig, null, null));
    }

    @Test void shouldDeclareCumulativeCapabilitiesOutsideTheComposer() {
        Set<CapabilityId> cumulative = new HashSet<>(Set.of(ID));
        CapabilityPolicyConfigValidator validator = CapabilityPolicyConfigValidator.forComposers(
                List.of(DEFINITION), List.of(), cumulative);
        cumulative.clear();
        CapabilityBundle first = bundle("first", new Budget(1));
        CapabilityBundle second = bundle("second", new Budget(2));
        assertFalse(validator.compete(first, second));
        assertTrue(new CapabilityPolicyConfigValidator(Map.of(ID, Budget.class), List.of(), Set.of()).compete(first, second));
        assertThrows(FrameworkException.class, () -> CapabilityPolicyConfigValidator.forComposers(
                List.of(DEFINITION), List.of(), Set.of(new CapabilityId("unknown"))));
    }

    @Test void shouldInvokeFullValidationIndependentlyOfPolicyConfig() {
        CapabilityPolicyConfigValidator validator = CapabilityPolicyConfigValidator.forComposers(List.of(DEFINITION), List.of(new CapabilityConfigRule<Budget>() {
            public CapabilityId id() { return ID; }
            public Class<Budget> configType() { return Budget.class; }
            public void validate(List<Budget> configs) {
                assertThrows(UnsupportedOperationException.class, configs::clear);
                if (configs.stream().mapToInt(Budget::value).sum() > 10) {
                    throw FrameworkException.invalidObject();
                }
            }
        }), Set.of());
        assertThrows(FrameworkException.class, () -> validator.validate(policyConfig(
                bundle("first", new Budget(6)), bundle("second", new Budget(6)))));
        assertEquals(new Budget(6), DEFINITION.compose(List.of(new Budget(6))));
    }

    @Test void shouldValidateWithoutComposingAndReuseTheRegisteredComposerForAssembly() {
        java.util.concurrent.atomic.AtomicInteger composed = new java.util.concurrent.atomic.AtomicInteger();
        CapabilityPolicyComposer<Budget, Budget> composer = new CapabilityPolicyComposer<>() {
            public CapabilityId id() { return ID; }
            public Class<Budget> configType() { return Budget.class; }
            public Class<Budget> policyType() { return Budget.class; }
            public Budget compose(List<Budget> configs) {
                composed.incrementAndGet();
                return DEFINITION.compose(configs);
            }
        };
        List<CapabilityPolicyComposer<?, ?>> registrations = new ArrayList<>(List.of(composer));
        CapabilityPolicyConfigValidator validator = CapabilityPolicyConfigValidator.forComposers(registrations, List.of(), Set.of());
        CapabilityPolicyAssembler assembler = new CapabilityPolicyAssembler(registrations, validator);
        registrations.clear();
        CapabilityBundle bundle = bundle("global", new Budget(4));
        validator.validate(policyConfig(bundle));
        assertEquals(0, composed.get());
        assertEquals(Set.of(ID), assembler.capabilities());
        assertThrows(UnsupportedOperationException.class, () -> assembler.capabilities().clear());
        CapabilityPolicyConfig policyConfig = new CapabilityPolicyConfig(java.util.List.of(bundle),
                List.of(binding("global", bundle, null)));
        assertEquals(new Budget(4), assembler.assemble(policyConfig, null, null).get(Budget.class));
        assertEquals(1, composed.get());
    }

    @Test void shouldRejectMissingRegistrationMetadataAndInputs() {
        CapabilityPolicyComposer<Budget, Budget> invalid = new CapabilityPolicyComposer<>() {
            public CapabilityId id() { return ID; }
            public Class<Budget> configType() { return null; }
            public Class<Budget> policyType() { return Budget.class; }
            public Budget compose(List<Budget> configs) { return new Budget(1); }
        };
        assertThrows(FrameworkException.class, () -> new CapabilityPolicyAssembler(List.of(invalid), validator()));
        assertThrows(FrameworkException.class, () -> new CapabilityPolicyAssembler(null, validator()));
        assertThrows(FrameworkException.class, () -> new CapabilityPolicyAssembler(Arrays.asList(DEFINITION, null), validator()));
        assertThrows(FrameworkException.class, () -> new CapabilityPolicyAssembler(List.of(DEFINITION), null));
        assertThrows(FrameworkException.class, () -> CapabilityPolicyConfigValidator.forComposers(List.of(DEFINITION), null, Set.of()));
    }

    @Test void shouldRejectValidatorCatalogsWithMissingAdditionalOrDifferentConfigTypes() {
        CapabilityId extra = new CapabilityId("extra");
        for (Map<CapabilityId, Class<? extends CapabilityConfig>> types : List.of(
                Map.<CapabilityId, Class<? extends CapabilityConfig>>of(),
                Map.<CapabilityId, Class<? extends CapabilityConfig>>of(ID, Budget.class, extra, Budget.class),
                Map.<CapabilityId, Class<? extends CapabilityConfig>>of(ID, TimeoutConfig.class))) {
            CapabilityPolicyConfigValidator validator = new CapabilityPolicyConfigValidator(types, List.of(), Set.of());
            assertThrows(FrameworkException.class, () -> new CapabilityPolicyAssembler(List.of(DEFINITION), validator));
        }
    }

    @Test void shouldNotUseAbsentNodesToDisableInheritedConfiguration() {
        TimeoutConfig timeout = new TimeoutConfig(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3));
        CapabilityBundle global = bundle("global", timeout);
        CapabilityBundle empty = bundle("empty");
        CapabilityPolicyConfig policyConfig = new CapabilityPolicyConfig(java.util.List.of(global, empty),
                List.of(binding("global", global, null), binding("endpoint", empty, endpoint().getId())));
        EffectiveCapabilityPolicies effective = assembler()
                .assemble(policyConfig, endpoint(), null);
        assertEquals(timeout, effective.get(EffectiveTimeoutPolicy.class).config());
    }

    private static CapabilityPolicyConfig policyConfig(CapabilityBundle... bundles) {
        return new CapabilityPolicyConfig(Arrays.asList(bundles), List.of());
    }

    private static CapabilityBundle bundle(String id, CapabilityConfig... configurations) {
        return new CapabilityBundle(new CapabilityBundleId(id), List.of(configurations));
    }
    private static CapabilityBinding binding(String id, CapabilityBundle bundle, EndpointId endpointId) {
        return new CapabilityBinding(new CapabilityBindingId(id), bundle.getBundleId(),
                null, endpointId, Set.of(), null, null, null);
    }
    private static Endpoint endpoint() {
        return new Endpoint(new EndpointId("read"), new ServiceId("catalog"), new RouteId("catalog"),
                List.of(new EndpointMatcher("GET", "/read")), Set.of(), EndpointStatus.ENABLED,
                new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                        SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of()));
    }
}
