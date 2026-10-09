package com.cloud.gateway.domain.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.framework.core.error.FrameworkException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AppAccessTest {

    @Test
    void shouldNormalizeAllowedOrigins() {
        AppAccess policy = new AppAccess(Set.of("HTTPS://EXAMPLE.COM:443", "http://localhost:8080"));

        assertEquals(Set.of("https://example.com", "http://localhost:8080"), policy.allowedOrigins());
        assertTrue(policy.allowsOrigin("https://example.com"));
        assertTrue(policy.allowsOrigin("HTTPS://EXAMPLE.COM:443"));
        assertFalse(policy.allowsOrigin("https://evil.example.com"));
    }

    @Test
    void shouldTreatMissingOriginsAsEmpty() {
        assertEquals(Set.of(), new AppAccess(null).allowedOrigins());
    }

    @Test
    void shouldNotMatchMalformedRequestOrigins() {
        AppAccess policy = new AppAccess(Set.of("https://example.com"));

        assertFalse(policy.allowsOrigin("not-an-origin"));
        assertFalse(policy.allowsOrigin("https://example.com/path"));
        assertFalse(policy.allowsOrigin(null));
    }

    @Test
    void shouldRejectOriginContainingNonOriginComponents() {
        assertThrows(FrameworkException.class, () -> new AppAccess(Set.of("https://example.com/path")));
        assertThrows(FrameworkException.class, () -> new AppAccess(Set.of("https://*.example.com")));
        assertThrows(FrameworkException.class, () -> new AppAccess(Set.of("https://user@example.com")));
        assertThrows(FrameworkException.class, () -> new AppAccess(Set.of("ftp://example.com")));
    }

    @Test
    void shouldTrimAndRequirePlatformOnApp() {
        App app = new App(new AppId("app"), " IOS ", AppStatus.ENABLED, new AppAccess(Set.of()));

        assertEquals("IOS", app.getPlatform());
        assertThrows(FrameworkException.class,
                () -> new App(new AppId("app"), " ", AppStatus.ENABLED, new AppAccess(Set.of())));
        assertThrows(FrameworkException.class,
                () -> new App(new AppId("app"), null, AppStatus.ENABLED, new AppAccess(Set.of())));
    }
}
