package com.cloud.gateway.domain.capability.header;

import com.cloud.gateway.domain.capability.header.rules.HeaderNameRules;
import com.cloud.gateway.domain.common.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HeaderNameRulesTest {
    @Test
    void shouldNormalizeValidNameToLowercaseForm() {
        assertEquals("x-gw-app", HeaderNameRules.normalize("X-GW-App"));
        assertEquals("content-type", HeaderNameRules.normalize("Content-Type"));
    }

    @Test
    void shouldRejectNullEmptyAndInvalidTokenCharacters() {
        assertThrows(DomainException.class, () -> HeaderNameRules.normalize(null));
        assertThrows(DomainException.class, () -> HeaderNameRules.normalize(""));
        assertThrows(DomainException.class, () -> HeaderNameRules.normalize("bad name"));
        assertThrows(DomainException.class, () -> HeaderNameRules.normalize("a\r\nb"));
    }

    @Test
    void shouldRejectNameBeyondLengthCap() {
        assertThrows(DomainException.class, () -> HeaderNameRules.normalize("a".repeat(129)));
        assertEquals("a".repeat(128), HeaderNameRules.normalize("a".repeat(128)));
    }
}
