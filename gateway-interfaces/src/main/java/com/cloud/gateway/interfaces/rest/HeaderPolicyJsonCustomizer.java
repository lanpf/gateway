package com.cloud.gateway.interfaces.rest;

import com.cloud.gateway.api.policy.PolicyCompositionApiCommand;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import java.io.IOException;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Component;

/** Header policy schema is strict even when other HTTP payloads tolerate unknown fields. */
@Component
public final class HeaderPolicyJsonCustomizer implements Jackson2ObjectMapperBuilderCustomizer {
    @Override
    public void customize(Jackson2ObjectMapperBuilder builder) {
        builder.postConfigurer(mapper -> mapper.addHandler(new DeserializationProblemHandler() {
            @Override
            public boolean handleUnknownProperty(DeserializationContext context, JsonParser parser,
                    JsonDeserializer<?> deserializer, Object target, String propertyName) throws IOException {
                if (target == PolicyCompositionApiCommand.Headers.class
                        || target instanceof PolicyCompositionApiCommand.Headers) {
                    throw JsonMappingException.from(parser, "Unknown header policy property");
                }
                return false;
            }
        }));
    }
}
