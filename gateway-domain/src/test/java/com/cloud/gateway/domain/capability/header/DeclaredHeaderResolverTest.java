package com.cloud.gateway.domain.capability.header;

import com.cloud.framework.core.RequestHeader;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeclaredHeaderResolverTest {
    @Test
    void shouldOnlyResolveExplicitlySupportedDeclarations() {
        assertEquals(Set.of(RequestHeader.APP_VERSION), DeclaredHeaderResolver.supportedHeaders());
        assertFalse(DeclaredHeaderResolver.supports(null));
        assertTrue(DeclaredHeaderResolver.resolve(null, "value").isEmpty());
        for (RequestHeader header : RequestHeader.values()) {
            assertEquals(header == RequestHeader.APP_VERSION, DeclaredHeaderResolver.supports(header));
            if (header != RequestHeader.APP_VERSION) {
                assertTrue(DeclaredHeaderResolver.resolve(header, "forged").isEmpty());
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> DeclaredHeaderResolver.supportedHeaders().clear());
    }

    @Test
    void shouldPreserveValidAppVersionIncludingMaximumLength() {
        for (String version : List.of("1.2.3-beta+4", "v1_ABC", "a".repeat(64))) {
            assertEquals(version, DeclaredHeaderResolver.resolve(RequestHeader.APP_VERSION, version).orElseThrow());
        }
    }

    @Test
    void shouldDropMissingEmptyInvalidAndOversizedVersionsWithoutTrimming() {
        assertTrue(DeclaredHeaderResolver.resolve(RequestHeader.APP_VERSION, null).isEmpty());
        for (String version : List.of("", " ", " 1.2.3", "1.2.3 ", "1\r\nInjected", "1\t2",
                "1\u00852", "版本", "a".repeat(65))) {
            assertTrue(DeclaredHeaderResolver.resolve(RequestHeader.APP_VERSION, version).isEmpty());
        }
    }
}
