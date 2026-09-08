package com.cloud.gateway.domain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.gateway.domain.common.DomainException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IngressClientAppPolicyTest {

    @Test
    void shouldNormalizeAllowedBrowserOrigins() {
        IngressClientAppPolicy policy = new IngressClientAppPolicy(
                new ClientAppId("browser-app"),
                ClientAppStatus.ENABLED,
                "WEB",
                Set.of("HTTPS://EXAMPLE.COM:443", "http://localhost:8080"));

        assertEquals(Set.of("https://example.com", "http://localhost:8080"),
                policy.allowedBrowserOrigins());
        assertTrue(policy.allowsBrowserOrigin("https://example.com"));
        assertTrue(policy.allowsBrowserOrigin("HTTPS://EXAMPLE.COM:443"));
        assertFalse(policy.allowsBrowserOrigin("https://evil.example.com"));
    }

    @Test
    void shouldRejectOriginContainingNonOriginComponents() {
        assertThrows(DomainException.class, () -> new IngressClientAppPolicy(
                new ClientAppId("browser-app"),
                ClientAppStatus.ENABLED,
                "WEB",
                Set.of("https://example.com/path")));
        assertThrows(DomainException.class, () -> new IngressClientAppPolicy(
                new ClientAppId("browser-app"),
                ClientAppStatus.ENABLED,
                "WEB",
                Set.of("https://*.example.com")));
    }
}
