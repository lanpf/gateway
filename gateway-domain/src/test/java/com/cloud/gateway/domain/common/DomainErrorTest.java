package com.cloud.gateway.domain.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DomainErrorTest {
    @Test
    void shouldKeepSharedDomainErrorCodesAtAuthorizedDevelopmentAllocation() {
        assertEquals("800000", DomainError.DOMAIN_ENTITY_ID_INVALID.getErrorCode());
        assertEquals("800001", DomainError.DOMAIN_OBJECT_FIELD_REQUIRED.getErrorCode());
        assertEquals("800002", DomainError.DOMAIN_OBJECT_FIELD_INVALID.getErrorCode());
        assertEquals("800003", DomainError.DOMAIN_OBJECT_STATE_INVALID.getErrorCode());
    }

    @Test
    void shouldNotExposeRemovedDomainEventIdError() {
        assertThrows(IllegalArgumentException.class,
                () -> DomainError.valueOf("DOMAIN_EVENT_ID_REQUIRED"));
    }
}
