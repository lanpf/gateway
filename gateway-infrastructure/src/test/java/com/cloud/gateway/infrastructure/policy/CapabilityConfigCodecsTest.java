package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.capability.policy.CapabilityPolicyAssembler;
import com.cloud.gateway.domain.capability.*;
import com.cloud.gateway.domain.capability.policy.*;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityPolicyConfigStorageMapStructMapperImpl;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityConfigCodecsTest {
    @Test void shouldRejectWireKeyMismatchAndDuplicateNormalizedIds() {
        CapabilityPolicyConfigStorageMapper mapper = new AbstractCapabilityPolicyConfigStorageMapStructMapperImpl(codecs);
        StoredCapabilityPolicyConfig.Bundle bundle = new StoredCapabilityPolicyConfig.Bundle("same", Map.of());
        assertThrows(com.cloud.framework.core.error.FrameworkException.class, () -> mapper.toDomain(
                new StoredCapabilityPolicyConfig(Map.of("different", bundle), Map.of())));
        assertThrows(com.cloud.framework.core.error.FrameworkException.class, () -> mapper.toDomain(
                new StoredCapabilityPolicyConfig(Map.of("same", bundle, " same ", bundle), Map.of())));
        StoredCapabilityPolicyConfig.Binding binding = new StoredCapabilityPolicyConfig.Binding(
                "all", "same", null, null, Set.of(), null, null, null);
        assertThrows(com.cloud.framework.core.error.FrameworkException.class, () -> mapper.toDomain(
                new StoredCapabilityPolicyConfig(Map.of("same", bundle), Map.of("other", binding))));
        assertThrows(com.cloud.framework.core.error.FrameworkException.class, () -> mapper.toDomain(
                new StoredCapabilityPolicyConfig(Map.of("same", bundle), Map.of("all", binding, " all ", binding))));
    }

    @Test void shouldKeepWireBindingMapSortedIndependentlyOfDomainInputOrder() {
        CapabilityPolicyConfigStorageMapper mapper = new AbstractCapabilityPolicyConfigStorageMapStructMapperImpl(codecs);
        CapabilityBundleId id = new CapabilityBundleId("same");
        CapabilityBundle bundle = new CapabilityBundle(id, List.of(new Budget(7)));
        CapabilityBinding a = new CapabilityBinding(new CapabilityBindingId("a"), id, null, null, Set.of(), null, null, null);
        CapabilityBinding z = new CapabilityBinding(new CapabilityBindingId("z"), id, null, null, Set.of(), null, null, null);
        StoredCapabilityPolicyConfig first = mapper.toStored(new CapabilityPolicyConfig(List.of(bundle), List.of(z, a)));
        StoredCapabilityPolicyConfig second = mapper.toStored(new CapabilityPolicyConfig(List.of(bundle), List.of(a, z)));
        assertEquals(first, second);
        assertEquals(List.of("a", "z"), first.bindings().values().stream().map(StoredCapabilityPolicyConfig.Binding::bindingId).toList());
        assertTrue(json.valueToTree(first).get("bindings").isObject());
    }

    private static final CapabilityId ID = new CapabilityId("test-budget");
    private record Budget(int value) implements CapabilityConfig {
        public CapabilityId id() { return ID; }
    }
    private record StoredBudget(int value) { }
    private static final CapabilityPolicyComposer<Budget, Budget> DEFINITION = new CapabilityPolicyComposer<>() {
        public CapabilityId id() { return ID; }
        public Class<Budget> configType() { return Budget.class; }
        public Class<Budget> policyType() { return Budget.class; }
        public Budget compose(List<Budget> orderedConfigs) { return orderedConfigs.isEmpty() ? new Budget(1) : orderedConfigs.get(orderedConfigs.size() - 1); }
    };
    private final ObjectMapper json = new ObjectMapper();
    private final CapabilityPolicyConfigValidator validator = CapabilityPolicyConfigValidator.forComposers(List.of(DEFINITION), List.of(), Set.of());
    private final CapabilityPolicyAssembler assembler = new CapabilityPolicyAssembler(List.of(DEFINITION), validator);
    private final CapabilityConfigCodec<Budget, StoredBudget> codec = new CapabilityConfigCodec<>(
            DEFINITION, StoredBudget.class, stored -> new Budget(stored.value()), value -> new StoredBudget(value.value()));
    private final CapabilityConfigCodecs codecs = new CapabilityConfigCodecs(validator, List.of(codec), json);

    @Test void shouldRoundTripAnAdditionalCapabilityThroughTheUnchangedBundleMapper() {
        CapabilityPolicyConfigStorageMapper mapper = new AbstractCapabilityPolicyConfigStorageMapStructMapperImpl(codecs);
        CapabilityBundleId id = new CapabilityBundleId("example");
        CapabilityPolicyConfig policyConfig = new CapabilityPolicyConfig(java.util.List.of(new CapabilityBundle(id, List.of(new Budget(7)))), List.of(
                        new CapabilityBinding(new CapabilityBindingId("global"), id,
                                null, null, Set.of(), null, null, null)));
        StoredCapabilityPolicyConfig stored = mapper.toStored(policyConfig);
        assertEquals(7, stored.bundles().get("example").capabilities().get(ID.value()).get("value").asInt());
        assertEquals(policyConfig, mapper.toDomain(stored));
        assertEquals(new Budget(7), assembler
                .assemble(mapper.toDomain(stored), null, null).get(Budget.class));
    }

    @Test void shouldRejectUnknownCapabilityUnknownFieldNullAndClassDiscriminators() throws Exception {
        assertThrows(InfrastructureException.class, () -> codecs.decode(Map.of("unknown", json.createObjectNode())));
        assertThrows(InfrastructureException.class, () -> codecs.decode(Map.of(ID.value(), json.nullNode())));
        assertThrows(InfrastructureException.class, () -> codecs.decode(Map.of(ID.value(), json.createArrayNode())));
        JsonNode unknown = json.readTree("{\"value\":1,\"unknown\":2}");
        JsonNode className = json.readTree("{\"value\":1,\"@class\":\"java.lang.Runtime\"}");
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(Map.of(ID.value(), unknown)));
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(Map.of(ID.value(), className)));
    }

    @Test void shouldRejectMissingAndDuplicateCodecsAtAssembly() {
        assertThrows(IllegalArgumentException.class, () -> new CapabilityConfigCodecs(validator, List.of(), json));
        assertThrows(com.cloud.framework.core.error.FrameworkException.class,
                () -> new CapabilityConfigCodecs(validator, List.of(codec, codec), json));
    }

    @Test void shouldRejectUnknownAndMismatchedCodecConfigurationTypesAtAssembly() {
        for (CapabilityPolicyConfigValidator incompatible : List.of(
                new CapabilityPolicyConfigValidator(Map.of(new CapabilityId("other"), Budget.class), List.of(), Set.of()),
                new CapabilityPolicyConfigValidator(Map.of(ID, com.cloud.gateway.domain.capability.resilience.TimeoutConfig.class), List.of(), Set.of()))) {
            assertThrows(IllegalArgumentException.class, () -> new CapabilityConfigCodecs(incompatible, List.of(codec), json));
        }
    }

    @Test void shouldCopyWireTreesAtConstructionAndOnRead() {
        com.fasterxml.jackson.databind.node.ObjectNode node = json.createObjectNode().put("value", 1);
        StoredCapabilityPolicyConfig.Bundle bundle = new StoredCapabilityPolicyConfig.Bundle("example", Map.of(ID.value(), node));
        node.put("value", 2);
        assertEquals(1, bundle.capabilities().get(ID.value()).get("value").asInt());
        ((com.fasterxml.jackson.databind.node.ObjectNode) bundle.capabilities().get(ID.value())).put("value", 3);
        assertEquals(1, bundle.capabilities().get(ID.value()).get("value").asInt());
    }
}
