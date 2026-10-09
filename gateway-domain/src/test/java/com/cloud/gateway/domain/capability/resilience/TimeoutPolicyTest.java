package com.cloud.gateway.domain.capability.resilience;

import com.cloud.gateway.domain.common.DomainException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloud.framework.core.error.FrameworkException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TimeoutPolicyTest {

    @Test
    void shouldValidateTimeoutsAtTransportResolution() {
        assertThrows(DomainException.class, () -> new TimeoutConfig(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        assertThrows(DomainException.class, () -> new TimeoutConfig(Duration.ofNanos(1), Duration.ofSeconds(1), Duration.ofSeconds(1)));
        assertThrows(DomainException.class, () -> new TimeoutConfig(Duration.ofDays(100), Duration.ofSeconds(1), Duration.ofSeconds(1)));
        assertThrows(FrameworkException.class, () -> new TimeoutConfig(null, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        assertEquals(Duration.ofSeconds(3), new TimeoutConfig(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3)).total());
    }
}
