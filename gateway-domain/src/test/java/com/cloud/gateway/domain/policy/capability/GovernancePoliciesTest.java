package com.cloud.gateway.domain.policy.capability;

import com.cloud.gateway.domain.common.DomainException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GovernancePoliciesTest {
    @Test
    void shouldValidateTimeoutsAtTransportResolution() {
        assertThrows(DomainException.class, () -> new TimeoutPolicy(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        assertThrows(DomainException.class, () -> new TimeoutPolicy(Duration.ofNanos(1), Duration.ofSeconds(1), Duration.ofSeconds(1)));
        assertThrows(DomainException.class, () -> new TimeoutPolicy(Duration.ofDays(100), Duration.ofSeconds(1), Duration.ofSeconds(1)));
        assertThrows(DomainException.class, () -> new TimeoutPolicy(null, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        assertEquals(Duration.ofSeconds(3), new TimeoutPolicy(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3)).total());
    }

    @Test
    void shouldValidateCircuitWindowAndFallback() {
        assertThrows(DomainException.class, () -> new CircuitBreakerPolicy(50, 50, Duration.ofSeconds(1),
                3, 2, 1, Duration.ofSeconds(1), CircuitBreakerPolicy.Fallback.ERROR));
        assertThrows(DomainException.class, () -> new CircuitBreakerPolicy(50, 50, Duration.ofSeconds(1),
                1, 2, 1, Duration.ZERO, CircuitBreakerPolicy.Fallback.ERROR));
        assertThrows(DomainException.class, () -> new CircuitBreakerPolicy(50, 50, Duration.ofSeconds(1),
                1, 2, 1, Duration.ofSeconds(1), null));
    }

    @Test
    void shouldNormalizeAndCopyCacheVariationHeadersAndBoundMemory() {
        HashSet<String> headers = new HashSet<>(Set.of("Accept-Language"));
        var policy = new CachePolicy(Duration.ofSeconds(1), Duration.ZERO, 1024, headers);
        headers.clear();
        assertEquals(Set.of("accept-language"), policy.varyHeaders());
        assertThrows(UnsupportedOperationException.class, () -> policy.varyHeaders().clear());
        assertThrows(DomainException.class, () -> new CachePolicy(Duration.ofSeconds(1), Duration.ZERO, 0, Set.of()));
        assertThrows(DomainException.class, () -> new CachePolicy(Duration.ofSeconds(1), Duration.ZERO, 1024, Set.of("x\r\n")));
    }
}
