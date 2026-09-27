package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.capability.header.HeaderConfig;
import com.cloud.gateway.domain.capability.header.rules.HeaderAdmissionRules;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import static org.junit.jupiter.api.Assertions.*;

class HeaderAdmissionPropertiesTest {
    @Test void shouldBindIndependentRestrictionsAndCaptureImmutableRules() {
        var source = new MapConfigurationPropertySource(Map.of(
                "gateway.header.extra[0]", "X-Deployment",
                "gateway.header.deny-set[0]", "X-Deployment",
                "gateway.header.deny-remove[0]", "X-Gateway-Audit"));
        HeaderAdmissionProperties properties = new Binder(source).bind("gateway.header",
                Bindable.of(HeaderAdmissionProperties.class)).orElseThrow(() -> new AssertionError("Header properties were not bound"));
        HeaderAdmissionRules rules = properties.toRules();
        assertThrows(DomainException.class, () -> rules.validate(new HeaderConfig(Set.of(),
                Map.of("x-deployment", "prod"), Set.of(), null)));
        assertDoesNotThrow(() -> rules.validate(new HeaderConfig(Set.of(), Map.of(), Set.of("X-Deployment"), null)));
        assertThrows(DomainException.class, () -> rules.validate(new HeaderConfig(Set.of(),
                Map.of(), Set.of("X-Gateway-Audit"), null)));
        assertThrows(UnsupportedOperationException.class, () -> properties.extra().add("X-Later"));
        assertFalse(rules.extra().contains("x-later"));
    }
}
