package com.cloud.gateway.domain.policy.header;

import com.cloud.gateway.domain.common.DomainException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeaderValueRulesTest {
    @Test
    void shouldAcceptEmptyAndBoundedValuesWithoutTrimming() {
        for (String value : List.of("", " ", "value", "a".repeat(4096))) {
            assertDoesNotThrow(() -> HeaderValueRules.requireValid(value));
            HeaderPolicy policy = new HeaderPolicy(Set.of(), Map.of("X-Gateway-Value", value), Set.of(), null);
            assertEquals(value, policy.setHeaders().get("X-Gateway-Value"));
        }
    }

    @Test
    void shouldRejectNullAndOversizedValuesInRulesAndPolicy() {
        assertThrows(DomainException.class, () -> HeaderValueRules.requireValid(null));
        assertThrows(DomainException.class, () -> HeaderValueRules.requireValid("a".repeat(4097)));
        Map<String, String> values = new HashMap<>();
        values.put("X-Gateway-Value", null);
        assertThrows(DomainException.class, () -> new HeaderPolicy(Set.of(), values, Set.of(), null));
        assertThrows(DomainException.class, () -> new HeaderPolicy(Set.of(),
                Map.of("X-Gateway-Value", "a".repeat(4097)), Set.of(), null));
    }

    @Test
    void shouldRejectAllIsoControlCharactersInRulesAndPolicy() {
        for (int codePoint = 0; codePoint <= 159; codePoint++) {
            if (Character.isISOControl(codePoint)) {
                String value = "before" + (char) codePoint + "after";
                assertThrows(DomainException.class, () -> HeaderValueRules.requireValid(value));
                assertThrows(DomainException.class, () -> new HeaderPolicy(Set.of(),
                        Map.of("X-Gateway-Value", value), Set.of(), null));
            }
        }
    }
}
