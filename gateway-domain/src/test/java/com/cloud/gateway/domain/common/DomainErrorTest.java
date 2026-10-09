package com.cloud.gateway.domain.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DomainErrorTest {
    @Test
    void shouldKeepSharedDomainErrorCodesAtAuthorizedDevelopmentAllocation() {
        assertEquals("800000", DomainError.DOMAIN_ENTITY_ID_INVALID.getErrorCode());
    }

    @Test
    void shouldNotExposeRetiredGenericDomainObjectErrors() {
        assertThrows(IllegalArgumentException.class, () -> DomainError.valueOf("DOMAIN_OBJECT_REQUIRED"));
        assertThrows(IllegalArgumentException.class, () -> DomainError.valueOf("DOMAIN_OBJECT_INVALID"));
        assertThrows(IllegalArgumentException.class, () -> DomainError.valueOf("DOMAIN_OBJECT_abnormal"));
    }

    @Test
    void shouldNotExposeRemovedDomainEventIdError() {
        assertThrows(IllegalArgumentException.class,
                () -> DomainError.valueOf("DOMAIN_EVENT_ID_REQUIRED"));
    }
}
