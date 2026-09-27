package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDimension;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitMode;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalTokenBucketFallbackTest {
    private final AtomicLong nanos = new AtomicLong();
    private final LocalTokenBucketFallback fallback = new LocalTokenBucketFallback(2, nanos::get);
    private final RateLimitRule rule = new RateLimitRule("stable", RateLimitDimension.IP, 1, 2, RateLimitMode.EXACT);

    @Test
    void shouldPreserveFractionalTokensAcrossRepeatedDenial() {
        assertTrue(fallback.tryAcquire(rule, "a", "a").allowed());
        nanos.set(125_000_000);
        assertEquals(Duration.ofMillis(375), fallback.tryAcquire(rule, "a", "a").retryAfter());
        nanos.set(250_000_000);
        assertEquals(Duration.ofMillis(250), fallback.tryAcquire(rule, "a", "a").retryAfter());
        nanos.set(500_000_000);
        assertTrue(fallback.tryAcquire(rule, "a", "a").allowed());
    }

    @Test
    void shouldPreserveBalanceAndSettleOldRateBeforeApplyingUpdatedConfiguration() {
        assertTrue(fallback.tryAcquire(rule, "a", "a").allowed());
        nanos.set(250_000_000);
        RateLimitRule updated = new RateLimitRule("stable", RateLimitDimension.IP, 100, 10, RateLimitMode.EXACT);
        RateLimitDecision decision = fallback.tryAcquire(updated, "a", "a");
        assertFalse(decision.allowed());
        assertEquals(Duration.ofMillis(50), decision.retryAfter());
        nanos.set(300_000_000);
        assertTrue(fallback.tryAcquire(updated, "a", "a").allowed());
    }

    @Test
    void shouldDenyNewKeysAtBoundWithoutEvictingDepletedBuckets() {
        assertTrue(fallback.tryAcquire(rule, "a", "a").allowed());
        assertTrue(fallback.tryAcquire(rule, "b", "b").allowed());
        assertTrue(fallback.tryAcquire(rule, "c", "c").unavailable());
        assertFalse(fallback.tryAcquire(rule, "a", "a").allowed());
        nanos.set(500_000_000);
        assertTrue(fallback.tryAcquire(rule, "c", "c").allowed());
    }

    @Test
    void shouldClampBalanceWhenCapacityIsReduced() {
        RateLimitRule large = new RateLimitRule("stable", RateLimitDimension.IP, 100, 1, RateLimitMode.EXACT);
        assertEquals(99, fallback.tryAcquire(large, "a", "a").remaining());
        assertEquals(0, fallback.tryAcquire(rule, "a", "a").remaining());
        assertFalse(fallback.tryAcquire(rule, "a", "a").allowed());
    }

    @Test
    void shouldSerializeConcurrentAcquisitions() throws Exception {
        java.util.concurrent.CopyOnWriteArrayList<Boolean> outcomes = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.ArrayList<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Thread thread = new Thread(() -> outcomes.add(fallback.tryAcquire(rule, "a", "a").allowed()));
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
    }
}
