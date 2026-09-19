package com.cloud.gateway.infrastructure.governance.config;

import com.cloud.gateway.application.governance.DownstreamCircuitBreaker;
import com.cloud.gateway.application.governance.ResponseCache;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceAutoConfigurationTest {
    @Test
    void shouldRegisterProtocolNeutralPortsWithExistingClock() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(Clock.class, () -> Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
            context.register(GovernanceAutoConfiguration.class);
            context.refresh();
            assertNotNull(context.getBean(ResponseCache.class));
            assertNotNull(context.getBean(DownstreamCircuitBreaker.class));
            assertEquals(1, context.getBeansOfType(Clock.class).size());
        }
    }
}
