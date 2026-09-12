package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.function.Function;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.util.StringUtils;

abstract class PolicyYamlCodec {
    private final ObjectMapper objectMapper;

    protected PolicyYamlCodec(ObjectMapper source) {
        this.objectMapper = source.copyWith(new YAMLFactory())
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    protected final <S, T> T decode(
            String content,
            TypeReference<S> type,
            Function<S, T> mapper
    ) {
        return decode(content, objectMapper.readerFor(type), mapper);
    }

    protected final <T> T decode(String content, TypeReference<T> type) {
        return decode(content, type, Function.identity());
    }

    protected final <S, T> T decode(
            String content,
            Class<S> type,
            Function<S, T> mapper
    ) {
        return decode(content, objectMapper.readerFor(type), mapper);
    }

    protected final <T> T decode(String content, Class<T> type) {
        return decode(content, type, Function.identity());
    }

    protected final String encode(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new InfrastructureException(
                    InfrastructureError.INFRA_TECH_POLICY_SERIALIZATION_FAILED, exception);
        }
    }

    private <S, T> T decode(String content, ObjectReader reader, Function<S, T> mapper) {
        if (!StringUtils.hasText(content)) {
            throw new InfrastructureException(
                    InfrastructureError.INFRA_TECH_POLICY_DESERIALIZATION_FAILED);
        }
        try {
            S source = reader.readValue(content);
            return mapper.apply(source);
        } catch (InfrastructureException exception) {
            throw exception;
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new InfrastructureException(
                    InfrastructureError.INFRA_TECH_POLICY_DESERIALIZATION_FAILED, exception);
        }
    }
}
