package com.cloud.gateway.interfaces.rest;

import com.cloud.framework.core.error.FrameworkError;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GatewayValidationExceptionHandlerTest {
    @Test
    void shouldExposeFrameworkCodeWithoutLeakingRejectedValues() {
        var response = new GatewayValidationExceptionHandler().validationFailure(
                new ConstraintViolationException("sensitive rejected value", Set.of()));
        assertEquals(400, response.getStatusCode().value());
        assertEquals(FrameworkError.OBJECT_INVALID.errorCode(), response.getBody().code());
        assertEquals(FrameworkError.OBJECT_INVALID.errorMessage(), response.getBody().message());
        assertNull(response.getBody().data());
    }
}
