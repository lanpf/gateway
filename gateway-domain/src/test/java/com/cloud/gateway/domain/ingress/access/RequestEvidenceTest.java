package com.cloud.gateway.domain.ingress.access;

import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.framework.core.error.FrameworkException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestEvidenceTest {

    @Test
    void shouldNormalizeMethodAndDeriveUnsafeFromTheSafeMethodSet() {
        RequestEvidence evidence = new RequestEvidence(
                " get ", "/orders", null, new AppId("app"), new ClientId("client"), null, "10.0.0.1");

        assertEquals("GET", evidence.method());
        assertFalse(evidence.unsafe());
        assertTrue(new RequestEvidence("post", "/orders", null, null, null, null, "10.0.0.1").unsafe());
        assertFalse(new RequestEvidence("HEAD", "/orders", null, null, null, null, "10.0.0.1").unsafe());
    }

    @Test
    void shouldKeepRawQueryAbsentEmptyAndVerbatim() {
        assertNull(new RequestEvidence("GET", "/a", null, null, null, null, "ip").rawQuery());
        assertEquals("", new RequestEvidence("GET", "/a", "", null, null, null, "ip").rawQuery());
        // Signed verbatim: raw query is never decoded, trimmed or rewritten.
        assertEquals(" b=1 ", new RequestEvidence("GET", "/a", " b=1 ", null, null, null, "ip").rawQuery());
    }

    @Test
    void shouldAllowNullableDeclarationsAndOriginButRejectBlanks() {
        assertNull(new RequestEvidence("GET", "/a", null, null, null, null, "ip").declaredAppId());
        assertNull(new RequestEvidence("GET", "/a", null, null, null, null, "ip").origin());
        assertEquals("https://e.com",
                new RequestEvidence("GET", "/a", null, null, null, " https://e.com ", "ip").origin());

        assertThrows(FrameworkException.class, () -> new RequestEvidence(" ", "/a", null, null, null, null, "ip"));
        assertThrows(FrameworkException.class, () -> new RequestEvidence("GET", " ", null, null, null, null, "ip"));
        assertThrows(FrameworkException.class, () -> new RequestEvidence("GET", "/a", null, null, null, " ", "ip"));
        assertThrows(FrameworkException.class, () -> new RequestEvidence("GET", "/a", null, null, null, null, " "));
    }
}
