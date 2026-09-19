package com.cloud.gateway.interfaces.rest;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import java.io.IOException;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.cloud.gateway.api.facade.policy.CapabilityCompositionApiCommand;
import org.springframework.stereotype.Component;

/** Header access schema is strict even when other HTTP payloads tolerate unknown fields. */
@Component
public final class HeaderPolicyJsonCustomizer implements Jackson2ObjectMapperBuilderCustomizer {
    @Override
    public void customize(Jackson2ObjectMapperBuilder builder) {
        builder.postConfigurer(mapper -> mapper.addHandler(new DeserializationProblemHandler() {
            @Override
            public boolean handleUnknownProperty(DeserializationContext context, JsonParser parser,
                    JsonDeserializer<?> deserializer, Object target, String propertyName) throws IOException {
                if (target == CapabilityCompositionApiCommand.HeadersApiCommand.class
                        || target instanceof CapabilityCompositionApiCommand.HeadersApiCommand) {
                    throw JsonMappingException.from(parser, "Unknown header access property");
                }
                return false;
            }
        }));
    }
}
