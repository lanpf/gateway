package com.cloud.gateway.domain.policy.capability;

import java.time.Duration;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public record TimeoutPolicy(Duration connect, Duration read, Duration total) {
    public TimeoutPolicy {
        validate(connect);
        validate(read);
        validate(total);
    }

    private static void validate(Duration value) {
        Require.notNull(value, DomainException::missingField);
        if (value.compareTo(Duration.ofMillis(1)) < 0
                || value.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
            throw DomainException.invalidField();
        }
    }
}
