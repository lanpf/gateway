package com.cloud.gateway.infrastructure.security.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cloud.gateway.infrastructure.security.spring.config.GatewaySecurityProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GatewaySecurityPropertiesTest {
    private static final String EXPECTED_MESSAGE =
            "The selected Access Token format is missing required configuration";

    @Test
    void shouldUseEnglishMessageWhenSelectedAccessTokenFormatIsNotConfigured() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.getAccessToken().setFormat(GatewaySecurityProperties.Format.SELF_CONTAINED);
        properties.getAccessToken().setIssuer("https://issuer.example");
        properties.getRequestSignature().setMaxClockSkew(Duration.ofMinutes(5));

        try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<String> messages = validator.validate(properties).stream()
                    .map(violation -> violation.getMessage()).collect(java.util.stream.Collectors.toSet());
            assertEquals(Set.of(EXPECTED_MESSAGE), messages);
        }
    }

    @Test
    void shouldBindDeploymentValuesFromExternalConfiguration() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                GatewaySecurityProperties.PREFIX + ".admin-audience", "gateway-admin",
                GatewaySecurityProperties.ACCESS_TOKEN_PREFIX + ".format",
                "REFERENCE",
                GatewaySecurityProperties.ACCESS_TOKEN_PREFIX + ".issuer", "https://issuer.example",
                GatewaySecurityProperties.ACCESS_TOKEN_PREFIX + ".reference.introspection-uri",
                "https://issuer.example/introspect",
                GatewaySecurityProperties.ACCESS_TOKEN_PREFIX + ".reference.client-endpointId", "gateway-client",
                GatewaySecurityProperties.ACCESS_TOKEN_PREFIX + ".reference.client-secret", "secret",
                GatewaySecurityProperties.PREFIX + ".request-signature.max-clock-skew", "5m",
                GatewaySecurityProperties.PREFIX + ".browser-session.service-endpointId", "identity-service")));

        GatewaySecurityProperties properties = binder.bind(
                GatewaySecurityProperties.PREFIX, Bindable.of(GatewaySecurityProperties.class))
                .orElseThrow(() -> new AssertionError("Gateway security properties were not bound"));

        assertEquals(GatewaySecurityProperties.Format.REFERENCE, properties.getAccessToken().getFormat());
        assertEquals(Duration.ofMinutes(5), properties.getRequestSignature().getMaxClockSkew());
        assertEquals("identity-service", properties.getBrowserSession().getServiceId());
    }
}
