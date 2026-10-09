package com.cloud.gateway.domain.capability.header;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.error.FrameworkException;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeaderConfigRuleTest {
    @Test void shouldAllowOrdinaryNamesWithoutAnExtraAllowList() {
        var rules = HeaderConfigRule.empty();
        assertDoesNotThrow(() -> rules.validate(List.of(HeaderConfig.defaults(null))));
        assertDoesNotThrow(() -> rules.validate(List.of(set("x-eXtra"))));
        assertDoesNotThrow(() -> rules.validate(List.of(set("x-GW-marker"))));
        assertDoesNotThrow(() -> rules.validate(List.of(set("X-Unknown"))));
        assertDoesNotThrow(() -> rules.validate(List.of(set("Host"))));
    }

    @Test void shouldApplySetAndRemoveRestrictionsIndependently() {
        var rules = new HeaderConfigRule(Set.of("HOST"), Set.of("X-Extra", "X-GW-Audit"));
        assertThrows(DomainException.class, () -> rules.validate(List.of(set("host"))));
        assertDoesNotThrow(() -> rules.validate(List.of(remove("host"))));
        assertDoesNotThrow(() -> rules.validate(List.of(set("x-extra"))));
        assertThrows(DomainException.class, () -> rules.validate(List.of(remove("x-extra"))));
        assertThrows(DomainException.class, () -> rules.validate(List.of(remove("X-GW-Audit"))));
        assertThrows(DomainException.class, () -> HeaderConfigRule.empty().validate(List.of(set("X-App-Id"))));
    }

    @Test void shouldCaptureImmutableCaseInsensitiveRulesAndRejectInvalidConfiguration() {
        HashSet<String> extras = new HashSet<>(Set.of("X-Extra"));
        var rules = new HeaderConfigRule(extras, Set.of());
        extras.clear();
        assertEquals(Set.of("x-extra"), rules.denySet());
        assertThrows(UnsupportedOperationException.class, () -> rules.denySet().clear());
        assertThrows(FrameworkException.class, () -> new HeaderConfigRule(Set.of("X-Bad Name"), Set.of()));
        assertEquals(Set.of("x-extra"), new HeaderConfigRule(Set.of("X-Extra", "x-extra"), Set.of()).denySet());
    }

    private static HeaderConfig set(String name) { return new HeaderConfig(Set.of(), Map.of(name, "value"), Set.of(), null); }
    private static HeaderConfig remove(String name) { return new HeaderConfig(Set.of(), Map.of(), Set.of(name), null); }
}
