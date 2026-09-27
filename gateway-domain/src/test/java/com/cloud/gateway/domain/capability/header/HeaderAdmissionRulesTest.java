package com.cloud.gateway.domain.capability.header;

import com.cloud.gateway.domain.capability.header.rules.HeaderAdmissionRules;
import com.cloud.gateway.domain.common.DomainException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeaderAdmissionRulesTest {
    @Test void shouldAdmitConfiguredExtraNamesAndGatewayPrefixOnly() {
        var rules = new HeaderAdmissionRules(Set.of("X-Extra"), Set.of(), Set.of());
        assertDoesNotThrow(() -> rules.validate(HeaderPolicy.defaults(null)));
        assertDoesNotThrow(() -> rules.validate(set("x-eXtra")));
        assertDoesNotThrow(() -> rules.validate(set("x-GW-marker")));
        assertThrows(DomainException.class, () -> rules.validate(set("X-Unknown")));
        assertThrows(DomainException.class, () -> rules.validate(set("Host")));
    }

    @Test void shouldApplySetAndRemoveRestrictionsIndependently() {
        var rules = new HeaderAdmissionRules(Set.of("Host", "X-Extra"), Set.of("HOST"), Set.of("X-Extra", "X-Gateway-Audit"));
        assertThrows(DomainException.class, () -> rules.validate(set("host")));
        assertDoesNotThrow(() -> rules.validate(remove("host")));
        assertDoesNotThrow(() -> rules.validate(set("x-extra")));
        assertThrows(DomainException.class, () -> rules.validate(remove("x-extra")));
        assertThrows(DomainException.class, () -> rules.validate(remove("X-Gateway-Audit")));
        assertThrows(DomainException.class, () -> new HeaderAdmissionRules(Set.of("X-App-Id"), Set.of(), Set.of()).validate(set("X-App-Id")));
    }

    @Test void shouldCaptureImmutableCaseInsensitiveRulesAndRejectInvalidConfiguration() {
        HashSet<String> extras = new HashSet<>(Set.of("X-Extra"));
        var rules = new HeaderAdmissionRules(extras, Set.of(), Set.of());
        extras.clear();
        assertEquals(Set.of("x-extra"), rules.extra());
        assertThrows(UnsupportedOperationException.class, () -> rules.extra().clear());
        assertThrows(DomainException.class, () -> new HeaderAdmissionRules(Set.of("X-Bad Name"), Set.of(), Set.of()));
        assertThrows(DomainException.class, () -> new HeaderAdmissionRules(Set.of("X-Extra", "x-extra"), Set.of(), Set.of()));
    }

    private static HeaderPolicy set(String name) { return new HeaderPolicy(Set.of(), Map.of(name, "value"), Set.of(), null); }
    private static HeaderPolicy remove(String name) { return new HeaderPolicy(Set.of(), Map.of(), Set.of(name), null); }
}
