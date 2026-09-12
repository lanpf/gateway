package com.cloud.gateway.domain.policy.header;

import com.cloud.framework.core.RequestHeader;
import com.cloud.gateway.domain.common.DomainException;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeaderPolicyTest {
    @Test void shouldProtectAllRegisteredHeadersFromOrdinaryOperations() {
        for (RequestHeader header : RequestHeader.values()) {
            String name = header.headerName().toUpperCase(Locale.ROOT);
            assertThrows(DomainException.class, () -> new HeaderPolicy(Set.of(), Map.of(name, "forged"), Set.of(), null));
            assertThrows(DomainException.class, () -> new HeaderPolicy(Set.of(), Map.of(), Set.of(name), null));
        }
    }

    @Test void shouldAllowOnlySupportedTrustedHeaders() {
        assertEquals(TrustedHeaderResolver.supportedHeaders(), HeaderPolicy.defaults(null).trustedHeaders());
        for (RequestHeader header : RequestHeader.values()) {
            if (TrustedHeaderResolver.supports(header)) {
                assertDoesNotThrow(() -> new HeaderPolicy(Set.of(header), Map.of(), Set.of(), null));
            } else {
                assertThrows(DomainException.class, () -> new HeaderPolicy(Set.of(header), Map.of(), Set.of(), null));
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> HeaderPolicy.defaults(null).trustedHeaders().clear());
    }

    @Test void shouldRejectDuplicateNamesAndCrossOperationConflicts() {
        assertThrows(DomainException.class, () -> new HeaderPolicy(Set.of(),
                Map.of("X-Test", "one", "x-test", "two"), Set.of(), null));
        assertThrows(DomainException.class, () -> new HeaderPolicy(Set.of(),
                Map.of("X-Test", "one"), Set.of("x-test"), null));
        assertThrows(DomainException.class, () -> new HeaderPolicy(Set.of(), Map.of(), Set.of("X-Test", "x-test"), null));
    }

    @Test void shouldRejectMalformedNamesAndUnsafeValuesBeforeAdmission() {
        for (String name : Set.of("", " X-Test", "X-Test ", "X-Gateway-Bad Name", "X-Test\r\n", "X".repeat(129))) {
            assertThrows(DomainException.class, () -> new HeaderPolicy(Set.of(), Map.of(name, "value"), Set.of(), null));
            assertThrows(DomainException.class, () -> new HeaderPolicy(Set.of(), Map.of(), Set.of(name), null));
        }
        for (String value : Set.of("ok\r\ninjected", "\t", "\0", "\u007f", "\u0085", "x".repeat(4097))) {
            assertThrows(DomainException.class, () -> new HeaderPolicy(Set.of(), Map.of("X-Test", value), Set.of(), null));
        }
        assertDoesNotThrow(() -> new HeaderPolicy(Set.of(), Map.of("X".repeat(128), "x".repeat(4096)), Set.of(), null));
        assertDoesNotThrow(() -> new HeaderPolicy(Set.of(), Map.of("X-Gateway-Empty", ""), Set.of(), null));
    }
}
