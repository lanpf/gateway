package com.cloud.gateway.domain.capability.policy;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.capability.resilience.EffectiveTimeoutPolicy;

import com.cloud.framework.core.error.FrameworkException;
import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;
import com.cloud.gateway.domain.capability.header.HeaderConfig;
import com.cloud.gateway.domain.capability.resilience.TimeoutConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityPolicyConfigIndexTest {
    @Test
    void shouldReadBundleConfigWithoutDependingOnAComposerAndRejectWrongTypes() {
        CapabilityBundle bundle = bundle("one", 1);
        assertEquals(Duration.ofSeconds(1), bundle.find(BuiltInCapabilityCatalog.TIMEOUT, TimeoutConfig.class)
                .orElseThrow().connect());
        assertTrue(bundle.find(BuiltInCapabilityCatalog.HEADER_APPLY, HeaderConfig.class).isEmpty());
        assertThrows(FrameworkException.class, () -> bundle.find(BuiltInCapabilityCatalog.TIMEOUT, HeaderConfig.class));
        assertThrows(FrameworkException.class, () -> bundle.find(null, TimeoutConfig.class));
        assertThrows(FrameworkException.class, () -> bundle.find(BuiltInCapabilityCatalog.TIMEOUT, null));
    }

    @Test
    void shouldIndexBothCollectionsAndDetachThemFromTheCaller() {
        CapabilityBundle bundle = bundle("one", 1);
        CapabilityBinding binding = binding("first", bundle);
        List<CapabilityBundle> bundles = new ArrayList<>(List.of(bundle));
        List<CapabilityBinding> bindings = new ArrayList<>(List.of(binding));
        CapabilityPolicyConfig policyConfig = new CapabilityPolicyConfig(bundles, bindings);
        bundles.clear();
        bindings.clear();
        assertSame(bundle, policyConfig.getBundles().get(bundle.getBundleId()));
        assertSame(binding, policyConfig.getBindings().get(binding.bindingId()));
        assertThrows(UnsupportedOperationException.class, () -> policyConfig.getBundles().clear());
        assertThrows(UnsupportedOperationException.class, () -> policyConfig.getBindings().clear());
    }

    @Test
    void shouldRejectRepeatedIdsEvenWhenEntriesAreIdenticalOrHaveDifferentContent() {
        CapabilityBundle first = bundle("one", 1);
        CapabilityBundle changed = bundle("one", 2);
        CapabilityBundle second = bundle("two", 3);
        CapabilityBinding binding = binding("same", first);
        assertThrows(DomainException.class, () -> new CapabilityPolicyConfig(List.of(first, first), List.of()));
        assertThrows(DomainException.class, () -> new CapabilityPolicyConfig(List.of(first, changed), List.of()));
        assertThrows(DomainException.class, () -> new CapabilityPolicyConfig(List.of(first), List.of(binding, binding)));
        assertThrows(DomainException.class, () -> new CapabilityPolicyConfig(List.of(first, second),
                List.of(binding, binding("same", second))));
    }

    @Test
    void shouldRejectNullEntriesAndDanglingReferences() {
        CapabilityBundle bundle = bundle("one", 1);
        assertThrows(FrameworkException.class, () -> new CapabilityPolicyConfig(Arrays.asList(bundle, null), List.of()));
        assertThrows(FrameworkException.class, () -> new CapabilityPolicyConfig(List.of(bundle),
                Arrays.asList(binding("one", bundle), null)));
        assertThrows(DomainException.class, () -> new CapabilityPolicyConfig(List.of(), List.of(binding("one", bundle))));
    }

    @Test
    void shouldCompareByIndexedContentAndResolveIndependentlyOfInputOrder() {
        CapabilityBundle first = bundle("one", 1);
        CapabilityBundle second = bundle("two", 2);
        CapabilityBinding a = binding("a", second);
        CapabilityBinding b = binding("b", second);
        CapabilityPolicyConfig forward = new CapabilityPolicyConfig(List.of(first, second), List.of(a, b));
        CapabilityPolicyConfig reverse = new CapabilityPolicyConfig(List.of(second, first), List.of(b, a));
        assertEquals(forward, reverse);
        assertEquals(forward.hashCode(), reverse.hashCode());
        CapabilityPolicyAssembler assembler = BuiltInCapabilityCatalog.assembler();
        assertEquals(assembler.assemble(forward, null, null), assembler.assemble(reverse, null, null));
        assertEquals(Duration.ofSeconds(2), assembler.assemble(forward, null, null)
                .get(EffectiveTimeoutPolicy.class).config().connect());
    }

    private static CapabilityBundle bundle(String id, int seconds) {
        return new CapabilityBundle(new CapabilityBundleId(id), List.of(new TimeoutConfig(
                Duration.ofSeconds(seconds), Duration.ofSeconds(3), Duration.ofSeconds(5))));
    }

    private static CapabilityBinding binding(String id, CapabilityBundle bundle) {
        return new CapabilityBinding(new CapabilityBindingId(id), bundle.getBundleId(),
                null, null, Set.of(), null, null, null);
    }
}
