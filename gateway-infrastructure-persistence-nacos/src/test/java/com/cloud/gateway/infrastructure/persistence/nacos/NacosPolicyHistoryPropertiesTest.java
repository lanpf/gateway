package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyHistoryProperties;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class NacosPolicyHistoryPropertiesTest {
    @Test
    void shouldBindIndependentHistoryAndRestClientProperties() {
        String prefix = NacosPolicyHistoryProperties.PREFIX;
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                prefix + ".page-size", "50",
                prefix + ".max-pages", "10",
                prefix + ".admin-access-token", "history-token",
                prefix + ".rest-client.connect-timeout", "3s",
                prefix + ".rest-client.read-timeout", "8s"));

        NacosPolicyHistoryProperties properties = new Binder(source)
                .bind(prefix, Bindable.of(NacosPolicyHistoryProperties.class))
                .orElseThrow(() -> new AssertionError("History properties were not bound"));

        assertEquals(50, properties.getPageSize());
        assertEquals(10, properties.getMaxPages());
        assertEquals("history-token", properties.getAdminAccessToken());
        assertEquals(Duration.ofSeconds(3), properties.getRestClient().getConnectTimeout());
        assertEquals(Duration.ofSeconds(8), properties.getRestClient().getReadTimeout());
    }
}
