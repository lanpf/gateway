package com.cloud.gateway.infrastructure.policy;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.gateway.domain.capability.*;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfigValidator;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.*;
import org.springframework.util.Assert;

/** Protocol-only dispatch. Merge remains in composers; configuration validity remains in the domain validator. */
public final class CapabilityConfigCodecs {
    private final CapabilityPolicyConfigValidator validator;
    private final Map<String, CapabilityConfigCodec<?, ?>> codecs;
    private final ObjectMapper mapper;
    private final ObjectMapper localMapper;

    public CapabilityConfigCodecs(CapabilityPolicyConfigValidator validator,
            List<CapabilityConfigCodec<?, ?>> codecs, ObjectMapper mapper) {
        Require.allNotNull(validator, codecs, mapper);
        this.validator = validator;
        this.mapper = mapper.copy().registerModule(new JavaTimeModule())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.localMapper = this.mapper.copy().addHandler(new IndexedPropertyCollections());
        NullSafe.forEach(codecs);
        for (CapabilityConfigCodec<?, ?> codec : codecs) {
            Assert.isTrue(validator.supports(codec.composer().id(), codec.composer().configType()),
                    "Codec must match the registered capability and configuration type");
        }
        Map<String, CapabilityConfigCodec<?, ?>> registered = Require.noDuplicates(
                codecs, codec -> codec.composer().id().value());
        Assert.isTrue(registered.size() == validator.capabilities().size(), "Missing capability codec");
        this.codecs = NullSafe.copyOf(registered);
    }

    public Map<String, JsonNode> fromProperties(Map<String, Map<String, Object>> configurations) {
        Map<String, JsonNode> nodes = new TreeMap<>();
        NullSafe.forEach(configurations,(id, values) -> nodes.put(id, localNode(require(id), values)));
        return Collections.unmodifiableMap(nodes);
    }

    private <C extends CapabilityConfig, S> JsonNode localNode(CapabilityConfigCodec<C, S> codec, Map<String, Object> values) {
        Objects.requireNonNull(values, "configuration");
        S stored = localMapper.convertValue(values, codec.storedType());
        return encode(codec, codec.decode().apply(stored));
    }

    /** Decodes to entries indexed by their own identity; transported keys only select the codec. */
    public List<CapabilityConfig> decode(Map<String, JsonNode> nodes) {
        Require.notNull(nodes);
        Map<CapabilityId, CapabilityConfig> decoded = new LinkedHashMap<>();
        NullSafe.forEach(nodes,(name, node) -> {
            CapabilityConfigCodec<?, ?> codec = require(name);
            if (node == null || !node.isObject()) {
                throw new InfrastructureException(InfrastructureError.INFRA_TECH_POLICY_DESERIALIZATION_FAILED);
            }
            CapabilityConfig configuration = decode(codec, node);
            decoded.put(codec.composer().id(), configuration);
        });
        return List.copyOf(decoded.values());
    }

    /** JSON-shaped transport values; unlike local properties, indexed objects are not arrays. */
    @org.mapstruct.Named("capabilityNodes")
    public List<CapabilityConfig> decodeValues(Map<String, Map<String, Object>> nodes) {
        Require.notNull(nodes);
        Map<String, JsonNode> trees = new LinkedHashMap<>();
        NullSafe.forEach(nodes,(name, value) -> trees.put(name, mapper.valueToTree(value)));
        return decode(trees);
    }

    private <C extends CapabilityConfig, S> C decode(CapabilityConfigCodec<C, S> codec, JsonNode node) {
        return CapabilityConfig.requireType(codec.composer().id(), codec.composer().configType(),
                codec.decode().apply(mapper.convertValue(node, codec.storedType())));
    }

    public Map<String, JsonNode> encode(Map<CapabilityId, CapabilityConfig> configurations) {
        Map<String, JsonNode> encoded = new TreeMap<>();
        NullSafe.forEach(configurations,(id, config) -> encoded.put(id.value(), encode(require(id.value()), config)));
        return Collections.unmodifiableMap(encoded);
    }

    private <C extends CapabilityConfig, S> JsonNode encode(CapabilityConfigCodec<C, S> codec, CapabilityConfig config) {
        return mapper.valueToTree(codec.encode().apply(CapabilityConfig.requireType(
                codec.composer().id(), codec.composer().configType(), config)));
    }

    private CapabilityConfigCodec<?, ?> require(String name) {
        CapabilityConfigCodec<?, ?> codec = codecs.get(name);
        if (codec == null) {
            throw new InfrastructureException(InfrastructureError.INFRA_TECH_POLICY_DESERIALIZATION_FAILED);
        }
        return codec;
    }

    @org.mapstruct.BeforeMapping
    public void validateSource(com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig source) {
        validator.validate(source);
    }

    @org.mapstruct.AfterMapping
    public void validateTarget(@org.mapstruct.MappingTarget com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig target) {
        validator.validate(target);
    }

}
