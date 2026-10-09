package com.cloud.gateway.domain.capability.header;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.RequestHeader;
import com.cloud.gateway.domain.capability.header.HeaderConfig;
import com.cloud.gateway.domain.capability.header.TrustedHeaderResolver;
import com.cloud.framework.core.error.FrameworkException;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeaderConfigTest {
    @Test void shouldStoreNormalizedNamesAndPreserveValuesInImmutableCopies() {
        Map<String, String> set = new java.util.HashMap<>(Map.of("X-Custom", " Value "));
        Set<String> remove = new java.util.HashSet<>(Set.of("X-Remove"));
        HeaderConfig config = new HeaderConfig(Set.of(), set, remove, null);
        set.clear();
        remove.clear();
        assertEquals(Map.of("x-custom", " Value "), config.setHeaders());
        assertEquals(Set.of("x-remove"), config.removeHeaders());
        assertEquals(config, new HeaderConfig(Set.of(), Map.of("x-custom", " Value "), Set.of("x-remove"), null));
        assertEquals(config.hashCode(), new HeaderConfig(Set.of(), Map.of("x-custom", " Value "), Set.of("x-remove"), null).hashCode());
        assertThrows(UnsupportedOperationException.class, () -> config.setHeaders().clear());
        assertThrows(UnsupportedOperationException.class, () -> config.removeHeaders().clear());
    }

    @Test void shouldProtectAllRegisteredHeadersFromOrdinaryOperations() {
        for (RequestHeader header : RequestHeader.values()) {
            String name = header.headerName().toUpperCase(Locale.ROOT);
            assertThrows(DomainException.class, () -> new HeaderConfig(Set.of(), Map.of(name, "forged"), Set.of(), null));
            assertThrows(DomainException.class, () -> new HeaderConfig(Set.of(), Map.of(), Set.of(name), null));
        }
    }

    @Test void shouldAllowOnlySupportedTrustedHeaders() {
        assertEquals(TrustedHeaderResolver.supportedHeaders(), HeaderConfig.defaults(null).trustedHeaders());
        for (RequestHeader header : RequestHeader.values()) {
            if (TrustedHeaderResolver.supports(header)) {
                assertDoesNotThrow(() -> new HeaderConfig(Set.of(header), Map.of(), Set.of(), null));
            } else {
                assertThrows(DomainException.class, () -> new HeaderConfig(Set.of(header), Map.of(), Set.of(), null));
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> HeaderConfig.defaults(null).trustedHeaders().clear());
    }

    @Test void shouldRejectDuplicateNamesAndCrossOperationConflicts() {
        assertThrows(DomainException.class, () -> new HeaderConfig(Set.of(),
                Map.of("X-Test", "one", "x-test", "two"), Set.of(), null));
        assertThrows(DomainException.class, () -> new HeaderConfig(Set.of(),
                Map.of("X-Test", "one"), Set.of("x-test"), null));
        assertThrows(DomainException.class, () -> new HeaderConfig(Set.of(), Map.of(), Set.of("X-Test", "x-test"), null));
    }

    @Test void shouldRejectMalformedNamesAndUnsafeValuesBeforeAdmission() {
        for (String name : Set.of("", " X-Test", "X-Test ", "X-Gateway-Bad Name", "X-Test\r\n", "X".repeat(129))) {
            assertThrows(FrameworkException.class, () -> new HeaderConfig(Set.of(), Map.of(name, "value"), Set.of(), null));
            assertThrows(FrameworkException.class, () -> new HeaderConfig(Set.of(), Map.of(), Set.of(name), null));
        }
        for (String value : Set.of("ok\r\ninjected", "\t", "\0", "\u007f", "\u0085", "x".repeat(4097))) {
            assertThrows(DomainException.class, () -> new HeaderConfig(Set.of(), Map.of("X-Test", value), Set.of(), null));
        }
        assertDoesNotThrow(() -> new HeaderConfig(Set.of(), Map.of("X".repeat(128), "x".repeat(4096)), Set.of(), null));
        assertDoesNotThrow(() -> new HeaderConfig(Set.of(), Map.of("X-Gateway-Empty", ""), Set.of(), null));
    }
}
