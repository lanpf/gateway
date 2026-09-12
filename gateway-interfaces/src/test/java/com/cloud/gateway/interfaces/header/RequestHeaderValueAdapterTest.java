package com.cloud.gateway.interfaces.header;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestHeaderValueAdapterTest {
    @Test
    void shouldPreserveValidAppVersionIncludingMaximumLength() {
        for (String version : List.of("1.2.3-beta+4", "v1_ABC", "a".repeat(64))) {
            assertEquals(version, RequestHeaderValueAdapter.sanitizedAppVersion(version).orElseThrow());
        }
    }

    @Test
    void shouldDropMissingEmptyInvalidAndOversizedVersionsWithoutTrimming() {
        assertTrue(RequestHeaderValueAdapter.sanitizedAppVersion(null).isEmpty());
        for (String version : List.of("", " ", " 1.2.3", "1.2.3 ", "1\r\nInjected", "1\t2",
                "1\u00852", "版本", "a".repeat(65))) {
            assertTrue(RequestHeaderValueAdapter.sanitizedAppVersion(version).isEmpty());
        }
    }
}
