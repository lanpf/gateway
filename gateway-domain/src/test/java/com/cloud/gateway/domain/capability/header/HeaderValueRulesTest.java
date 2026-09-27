package com.cloud.gateway.domain.capability.header;

import com.cloud.gateway.domain.capability.header.rules.HeaderValueRules;
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
            assertDoesNotThrow(() -> HeaderValueRules.validate(value));
            HeaderConfig policy = new HeaderConfig(Set.of(), Map.of("X-Gateway-Value", value), Set.of(), null);
            assertEquals(value, policy.setHeaders().get("X-Gateway-Value"));
        }
    }

    @Test
    void shouldRejectNullAndOversizedValuesInRulesAndPolicy() {
        assertThrows(DomainException.class, () -> HeaderValueRules.validate(null));
        assertThrows(DomainException.class, () -> HeaderValueRules.validate("a".repeat(4097)));
        Map<String, String> values = new HashMap<>();
        values.put("X-Gateway-Value", null);
        assertThrows(DomainException.class, () -> new HeaderConfig(Set.of(), values, Set.of(), null));
        assertThrows(DomainException.class, () -> new HeaderConfig(Set.of(),
                Map.of("X-Gateway-Value", "a".repeat(4097)), Set.of(), null));
    }

    @Test
    void shouldRejectAllIsoControlCharactersInRulesAndPolicy() {
        for (int codePoint = 0; codePoint <= 159; codePoint++) {
            if (Character.isISOControl(codePoint)) {
                String value = "before" + (char) codePoint + "after";
                assertThrows(DomainException.class, () -> HeaderValueRules.validate(value));
                assertThrows(DomainException.class, () -> new HeaderConfig(Set.of(),
                        Map.of("X-Gateway-Value", value), Set.of(), null));
            }
        }
    }
}
