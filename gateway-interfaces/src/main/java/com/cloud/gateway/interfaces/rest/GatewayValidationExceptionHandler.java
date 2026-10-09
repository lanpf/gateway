package com.cloud.gateway.interfaces.rest;

import com.cloud.framework.core.Result;
import com.cloud.framework.core.error.FrameworkError;
import com.cloud.framework.core.error.FrameworkException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** One protocol boundary for request binding and proxied Facade validation failures. */
@RestControllerAdvice
public final class GatewayValidationExceptionHandler {
    @ExceptionHandler({WebExchangeBindException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class, MethodValidationException.class})
    public ResponseEntity<Result<Void>> validationFailure(Exception exception) {
        FrameworkException translated = new FrameworkException(FrameworkError.OBJECT_INVALID, exception);
        return ResponseEntity.badRequest().body(Result.failure(translated));
    }
}
