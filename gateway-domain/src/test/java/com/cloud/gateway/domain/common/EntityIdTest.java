package com.cloud.gateway.domain.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloud.gateway.domain.endpoint.EndpointId;
import org.junit.jupiter.api.Test;

class EntityIdTest {
    @Test
    void shouldNormalizeValidStringEntityId() {
        assertEquals("ORDER.GET", new EndpointId("  ORDER.GET  ").value());
    }

    @Test
    void shouldRejectBlankStringEntityIdWithStableError() {
        DomainException exception = assertThrows(DomainException.class, () -> new EndpointId(" "));

        assertEquals(DomainError.DOMAIN_ENTITY_ID_INVALID.getErrorCode(), exception.getErrorCode());
    }

    @Test
    void shouldRejectNonPositiveLongEntityIdWithStableError() {
        DomainException exception = assertThrows(DomainException.class, () -> new TestLongId(0L));

        assertEquals(DomainError.DOMAIN_ENTITY_ID_INVALID.getErrorCode(), exception.getErrorCode());
    }

    private static final class TestLongId extends LongEntityId {
        private TestLongId(Long value) {
            super(value);
        }
    }
}
