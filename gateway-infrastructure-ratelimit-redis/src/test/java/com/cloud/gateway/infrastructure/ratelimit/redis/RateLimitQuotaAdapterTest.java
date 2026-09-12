package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.framework.core.naming.DefaultResourceNameResolver;
import com.cloud.gateway.domain.policy.capability.*;
import com.cloud.gateway.application.quota.QuotaCommand;
import com.cloud.gateway.application.quota.QuotaCommandService;
import com.cloud.gateway.application.quota.QuotaObserver;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitQuotaAdapterTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RateLimitKeyResolver keys = new RateLimitKeyResolver(new DefaultResourceNameResolver());
    private final RateLimitQuotaAdapter adapter = new RateLimitQuotaAdapter(
            new RedisTokenBucketLimiter(redis), keys);
    private final RateLimitPolicy rule = new RateLimitPolicy("stable", RateLimitDimension.CLIENT, 1, 1, RateLimitMode.EXACT);

    @Test
    void shouldReturnExactFractionalRetryAfterFromScript() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 0L, 125L));
        RateLimitDecision decision = adapter.tryAcquire(rule, "partner");
        assertFalse(decision.allowed());
        assertFalse(decision.unavailable());
        assertEquals(Duration.ofMillis(125), decision.retryAfter());
        verify(redis).execute(any(RedisScript.class), eq(List.of(keys.bucket(rule, "partner"))), eq("1"), eq("1"));
    }

    @Test
    void shouldKeepPhysicalKeyAcrossQuotaConfigurationUpdates() {
        RateLimitPolicy revised = new RateLimitPolicy("stable", RateLimitDimension.CLIENT, 100, 30, RateLimitMode.EXACT);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 0L, 0L));
        adapter.tryAcquire(rule, "partner");
        adapter.tryAcquire(revised, "partner");
        verify(redis).execute(any(RedisScript.class), eq(List.of(keys.bucket(rule, "partner"))), eq("1"), eq("1"));
        verify(redis).execute(any(RedisScript.class), eq(List.of(keys.bucket(rule, "partner"))), eq("100"), eq("30"));
        assertEquals(keys.bucket(rule, "partner"), keys.bucket(revised, "partner"));
    }

    @Test
    void shouldReturnUnavailableRatherThanQuotaDenialWhenClosed() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("unreachable"));
        RateLimitDecision decision = adapter.tryAcquire(rule, "partner");
        assertFalse(decision.allowed());
        assertTrue(decision.unavailable());
        assertEquals(Duration.ZERO, decision.retryAfter());
    }

    @Test
    void shouldBoundLocalFallbackAndPreserveItAcrossRedisRecovery() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("unreachable"))
                .thenReturn(List.of(1L, 0L, 0L))
                .thenThrow(new RedisConnectionFailureException("unreachable"));
        QuotaCommandService service = new QuotaCommandService(adapter,
                new LocalRateLimitQuotaAdapter(keys, new RedisRateLimitProperties(), () -> 0L), QuotaObserver.noop());
        QuotaCommand command = new QuotaCommand(List.of(new RateLimitRules(List.of(rule), RedisFailurePolicy.OPEN_WITH_LOCAL)),
                Map.of(RateLimitDimension.CLIENT, "partner"), false);
        assertTrue(service.acquire(command, new HashSet<>()).allowed());
        assertTrue(service.acquire(command, new HashSet<>()).allowed());
        assertFalse(service.acquire(command, new HashSet<>()).allowed());
    }

    @Test
    void shouldFailClosedOnMalformedScriptResponse() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 999L, 0L), List.of(0L, 0L, 0L), List.of(1L, 0L), null);
        for (int i = 0; i < 4; i++) {
            assertTrue(adapter.tryAcquire(rule, "partner").unavailable());
        }
    }

    @Test
    void shouldNotSwallowProgrammingErrors() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("programming error"));
        assertThrows(IllegalStateException.class, () -> adapter.tryAcquire(rule, "partner"));
    }
}
