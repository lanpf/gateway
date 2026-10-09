package com.cloud.gateway.domain.capability.header;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.error.FrameworkException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeaderConfigValueTest {
    @Test
    void shouldAcceptEmptyAndBoundedValuesWithoutTrimming() {
        for (String value : List.of("", " ", "value", "a".repeat(4096))) {
            HeaderConfig policy = new HeaderConfig(Set.of(), Map.of("X-Gateway-Value", value), Set.of(), null);
            assertEquals(value, policy.setHeaders().get("x-gateway-value"));
        }
    }

    @Test
    void shouldRejectNullAndOversizedValues() {
        Map<String, String> values = new HashMap<>();
        values.put("X-Gateway-Value", null);
        assertThrows(FrameworkException.class, () -> new HeaderConfig(Set.of(), values, Set.of(), null));
        assertThrows(DomainException.class, () -> new HeaderConfig(Set.of(),
                Map.of("X-Gateway-Value", "a".repeat(4097)), Set.of(), null));
    }

    @Test
    void shouldRejectAllIsoControlCharacters() {
        for (int codePoint = 0; codePoint <= 159; codePoint++) {
            if (Character.isISOControl(codePoint)) {
                String value = "before" + (char) codePoint + "after";
                assertThrows(DomainException.class, () -> new HeaderConfig(Set.of(),
                        Map.of("X-Gateway-Value", value), Set.of(), null));
            }
        }
    }
}
