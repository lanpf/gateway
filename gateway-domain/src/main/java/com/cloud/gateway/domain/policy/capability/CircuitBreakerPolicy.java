package com.cloud.gateway.domain.policy.capability;

import java.time.Duration;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public record CircuitBreakerPolicy(
        int failureRateThreshold,
        int slowCallRateThreshold,
        Duration slowCallDuration,
        int minimumCalls,
        int slidingWindowSize,
        int halfOpenCalls,
        Duration waitDuration,
        Fallback fallback
) {
    public enum Fallback { ERROR, STALE_CACHE }

    public CircuitBreakerPolicy {
        Require.notNull(slowCallDuration, DomainException::missingField);
        Require.notNull(waitDuration, DomainException::missingField);
        Require.notNull(fallback, DomainException::missingField);
        if (failureRateThreshold < 1 || failureRateThreshold > 100
                || slowCallRateThreshold < 1 || slowCallRateThreshold > 100
                || minimumCalls < 1 || slidingWindowSize < minimumCalls || slidingWindowSize > 100000
                || halfOpenCalls < 1 || halfOpenCalls > slidingWindowSize
                || slowCallDuration.compareTo(Duration.ofMillis(1)) < 0
                || slowCallDuration.compareTo(Duration.ofDays(1)) > 0
                || waitDuration.compareTo(Duration.ofMillis(1)) < 0
                || waitDuration.compareTo(Duration.ofDays(1)) > 0) {
            throw DomainException.invalidField();
        }
    }
}
