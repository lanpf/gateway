package com.cloud.gateway.domain.security;

import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubjectAccessRulesTest {
    @Test
    void shouldChooseOneAuthenticationFamilyForEveryRequirement() {
        assertEquals(SubjectAuthenticationSelection.HOST, SubjectAuthenticationSelection.from(SubjectAuthenticationRequirement.HOST_SESSION_REQUIRED));
        assertEquals(SubjectAuthenticationSelection.HOST, SubjectAuthenticationSelection.from(SubjectAuthenticationRequirement.HOST_SESSION_OPTIONAL));
        assertEquals(SubjectAuthenticationSelection.BROWSER, SubjectAuthenticationSelection.from(SubjectAuthenticationRequirement.BROWSER_SESSION_REQUIRED));
        assertEquals(SubjectAuthenticationSelection.IGNORE, SubjectAuthenticationSelection.from(SubjectAuthenticationRequirement.IGNORE));
    }

    @Test
    void shouldRequireEveryScopeAndAudience() {
        assertTrue(SubjectAccessRules.hasRequiredScopes(Set.of("read", "write"), Set.of("read")));
        assertFalse(SubjectAccessRules.hasRequiredScopes(Set.of("read"), Set.of("read", "write")));
        assertTrue(SubjectAccessRules.hasRequiredAudiences(Set.of("gateway", "orders"), Set.of("gateway")));
        assertFalse(SubjectAccessRules.hasRequiredAudiences(Set.of("gateway"), Set.of("gateway", "orders")));
    }

    @Test
    void shouldRequireHostAndBothManagementClaims() {
        ManagementAccessRules rules = new ManagementAccessRules("gateway");
        assertTrue(rules.allows(SubjectType.HOST_SESSION, Set.of("admin"), Set.of("gateway")));
        assertFalse(rules.allows(SubjectType.HOST_SESSION, Set.of("read"), Set.of("gateway")));
        assertFalse(rules.allows(SubjectType.HOST_SESSION, Set.of("admin"), Set.of("orders")));
        assertFalse(rules.allows(SubjectType.BROWSER_SESSION, Set.of("admin"), Set.of("gateway")));
    }
}
