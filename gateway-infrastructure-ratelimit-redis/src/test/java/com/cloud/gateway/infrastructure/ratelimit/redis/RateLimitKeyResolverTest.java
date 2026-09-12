package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.framework.core.naming.DefaultResourceNameResolver;
import com.cloud.gateway.domain.policy.capability.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RateLimitKeyResolverTest {
    private final RateLimitKeyResolver keys = new RateLimitKeyResolver(new DefaultResourceNameResolver());

    @Test
    void shouldIsolateRulesDimensionsAndUntrustedSegments() {
        RateLimitPolicy rule = new RateLimitPolicy("a", RateLimitDimension.APP, 1, 1, RateLimitMode.EXACT);
        RateLimitPolicy other = new RateLimitPolicy("b", RateLimitDimension.APP, 1, 1, RateLimitMode.EXACT);
        RateLimitPolicy dimension = new RateLimitPolicy("a", RateLimitDimension.CLIENT, 1, 1, RateLimitMode.EXACT);
        assertNotEquals(keys.bucket(rule, "value"), keys.bucket(other, "value"));
        assertNotEquals(keys.bucket(rule, "value"), keys.bucket(dimension, "value"));
        assertNotEquals(keys.bucket(rule, "a:b"), keys.bucket(rule, "a/b"));
        assertFalse(keys.bucket(rule, "{user}:value").contains("{user}"));
        assertThrows(IllegalArgumentException.class, () -> keys.bucket(rule, " "));
    }

    @Test
    void shouldDelegateNamespaceResolutionExactlyOnce() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        RateLimitKeyResolver resolver = new RateLimitKeyResolver(segments -> {
            calls.incrementAndGet();
            return "gateway:" + String.join(":", segments);
        });
        RateLimitPolicy rule = new RateLimitPolicy("a", RateLimitDimension.APP, 1, 1, RateLimitMode.EXACT);
        assertTrue(resolver.bucket(rule, "value").startsWith("gateway:ratelimit:"));
        assertEquals(1, calls.get());
    }
}
