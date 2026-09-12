package com.cloud.gateway.domain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.gateway.domain.common.DomainException;
import java.util.Set;

import com.cloud.gateway.domain.app.AppId;
import org.junit.jupiter.api.Test;

class AppPolicyTest {

    @Test
    void shouldAllowAccessAndPlatformChangesButRejectIdentityReassignment() {
        AppPolicy original = new AppPolicy(new AppId("app"), "WEB", Set.of(), PolicyStatus.ENABLED);
        AppPolicy disabled = new AppPolicy(original.appId(), "WEB", Set.of("https://example.com"), PolicyStatus.DISABLED);
        disabled.validateUpdateFrom(original);
        AppPolicy corrected = new AppPolicy(original.appId(), " IOS ", Set.of(), PolicyStatus.ENABLED);
        corrected.validateUpdateFrom(original);
        assertEquals("IOS", corrected.platform());
        assertEquals("WEB", original.platform());
        assertThrows(DomainException.class, () -> new AppPolicy(new AppId("other"), "WEB", Set.of(), PolicyStatus.ENABLED)
                .validateUpdateFrom(original));
    }

    @Test
    void shouldRejectMissingOrBlankPlatform() {
        AppId id = new AppId("app");
        assertThrows(DomainException.class, () -> new AppPolicy(id, null, Set.of(), PolicyStatus.ENABLED));
        assertThrows(DomainException.class, () -> new AppPolicy(id, " ", Set.of(), PolicyStatus.ENABLED));
    }

    @Test
    void shouldNormalizeAllowedOrigins() {
        AppPolicy policy = new AppPolicy(
                new AppId("browser-app"),
                "WEB",
                Set.of("HTTPS://EXAMPLE.COM:443", "http://localhost:8080"),
                PolicyStatus.ENABLED);

        assertEquals(Set.of("https://example.com", "http://localhost:8080"),
                policy.allowedOrigins());
        assertTrue(policy.allowsOrigin("https://example.com"));
        assertTrue(policy.allowsOrigin("HTTPS://EXAMPLE.COM:443"));
        assertFalse(policy.allowsOrigin("https://evil.example.com"));
    }

    @Test
    void shouldRejectOriginContainingNonOriginComponents() {
        assertThrows(DomainException.class, () -> new AppPolicy(
                new AppId("browser-app"),
                "WEB",
                Set.of("https://example.com/path"),
                PolicyStatus.ENABLED));
        assertThrows(DomainException.class, () -> new AppPolicy(
                new AppId("browser-app"),
                "WEB",
                Set.of("https://*.example.com"),
                PolicyStatus.ENABLED));
    }
}
