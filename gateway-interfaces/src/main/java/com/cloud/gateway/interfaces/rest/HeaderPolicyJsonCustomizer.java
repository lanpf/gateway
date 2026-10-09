package com.cloud.gateway.interfaces.rest;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import java.io.IOException;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.cloud.gateway.api.facade.admin.definition.CapabilityPolicyConfigApiCommand;
import org.springframework.stereotype.Component;

/** Reject duplicate JSON keys; keep Header fields strict even when other payloads tolerate unknown fields. */
@Component
public final class HeaderPolicyJsonCustomizer implements Jackson2ObjectMapperBuilderCustomizer {
    @Override
    public void customize(Jackson2ObjectMapperBuilder builder) {
        builder.featuresToEnable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        builder.postConfigurer(mapper -> mapper.addHandler(new DeserializationProblemHandler() {
            @Override
            public boolean handleUnknownProperty(DeserializationContext context, JsonParser parser,
                    JsonDeserializer<?> deserializer, Object target, String propertyName) throws IOException {
                if (target == CapabilityPolicyConfigApiCommand.HeadersApiCommand.class
                        || target instanceof CapabilityPolicyConfigApiCommand.HeadersApiCommand) {
                    throw JsonMappingException.from(parser, "Unknown header access property");
                }
                return false;
            }
        }));
    }
}
