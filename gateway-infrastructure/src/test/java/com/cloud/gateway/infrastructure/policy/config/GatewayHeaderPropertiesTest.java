package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.policy.header.HeaderPolicy;
import com.cloud.gateway.domain.policy.header.HeaderRules;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import static org.junit.jupiter.api.Assertions.*;

class GatewayHeaderPropertiesTest {
    @Test void shouldBindIndependentRestrictionsAndCaptureImmutableRules() {
        var source = new MapConfigurationPropertySource(Map.of(
                "gateway.headers.extra-headers[0]", "X-Deployment",
                "gateway.headers.forbidden-set-headers[0]", "X-Deployment",
                "gateway.headers.forbidden-remove-headers[0]", "X-Gateway-Audit"));
        GatewayHeaderProperties properties = new Binder(source).bind("gateway.headers",
                Bindable.of(GatewayHeaderProperties.class)).orElseThrow(() -> new AssertionError("Header properties were not bound"));
        HeaderRules rules = properties.toRules();
        assertThrows(DomainException.class, () -> rules.validate(new HeaderPolicy(Set.of(),
                Map.of("x-deployment", "prod"), Set.of(), null)));
        assertDoesNotThrow(() -> rules.validate(new HeaderPolicy(Set.of(), Map.of(), Set.of("X-Deployment"), null)));
        assertThrows(DomainException.class, () -> rules.validate(new HeaderPolicy(Set.of(),
                Map.of(), Set.of("X-Gateway-Audit"), null)));
        properties.getExtraHeaders().add("X-Later");
        assertFalse(rules.extraHeaders().contains("x-later"));
    }
}
