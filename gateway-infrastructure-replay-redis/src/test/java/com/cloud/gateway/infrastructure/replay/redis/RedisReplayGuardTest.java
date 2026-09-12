package com.cloud.gateway.infrastructure.replay.redis;

import static org.junit.jupiter.api.Assertions.*;

import com.cloud.framework.core.naming.DefaultResourceNameResolver;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

class RedisReplayGuardTest {
    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");

    @Test
    void shouldUseOneAtomicSetIfAbsentWithExpirationAndRejectDuplicates() {
        StubRedisTemplate redis = new StubRedisTemplate();
        RedisReplayGuard guard = guard(redis);
        assertTrue(guard.register("digest", NOW.plusSeconds(600)).toCompletableFuture().join());
        assertFalse(guard.register("digest", NOW.plusSeconds(600)).toCompletableFuture().join());
        assertEquals("replay:digest", redis.lastKey);
        assertEquals(Duration.ofSeconds(600), redis.lastTtl);
        assertEquals(2, redis.calls.get());
    }

    @Test
    void shouldRoundUpSubMillisecondTtlAndRejectExpiredEvidenceWithoutRedis() {
        StubRedisTemplate redis = new StubRedisTemplate();
        RedisReplayGuard guard = guard(redis);
        assertFalse(guard.register("expired", NOW).toCompletableFuture().join());
        assertFalse(guard.register("expired", NOW.minusNanos(1)).toCompletableFuture().join());
        assertEquals(0, redis.calls.get());
        assertTrue(guard.register("live", NOW.plusNanos(1)).toCompletableFuture().join());
        assertEquals(Duration.ofMillis(1), redis.lastTtl);
    }

    @Test
    void shouldAllowOnlyOneConcurrentRegistrationAcrossGuardInstances() {
        StubRedisTemplate redis = new StubRedisTemplate();
        RedisReplayGuard first = guard(redis);
        RedisReplayGuard second = guard(redis);
        List<CompletableFuture<Boolean>> attempts = IntStream.range(0, 32).mapToObj(index -> CompletableFuture.supplyAsync(() ->
                (index % 2 == 0 ? first : second).register("shared", NOW.plusSeconds(600))
                        .toCompletableFuture().join())).toList();
        assertEquals(1, attempts.stream().map(CompletableFuture::join).filter(Boolean.TRUE::equals).count());
        assertEquals(32, redis.calls.get());
    }

    @Test
    void shouldPropagateRedisFailureAndEmptyReplyAsDependencyFailure() {
        StubRedisTemplate redis = new StubRedisTemplate();
        RedisReplayGuard guard = guard(redis);
        redis.reply = Mono.error(new IllegalStateException("unavailable"));
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> guard.register("key", NOW.plusSeconds(10)).toCompletableFuture().join());
        redis.reply = Mono.empty();
        java.util.concurrent.CompletionException failure = assertThrows(java.util.concurrent.CompletionException.class,
                () -> guard.register("key", NOW.plusSeconds(10)).toCompletableFuture().join());
        assertInstanceOf(org.springframework.dao.InvalidDataAccessResourceUsageException.class, failure.getCause());
    }

    private static RedisReplayGuard guard(StubRedisTemplate redis) {
        return new RedisReplayGuard(redis, new ReplayGuardKeyResolver(new DefaultResourceNameResolver()),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** Only the injected Redis collaborator is stubbed; no server is used in this module test. */
    private static final class StubRedisTemplate extends ReactiveStringRedisTemplate {
        private final Set<String> registered = ConcurrentHashMap.newKeySet();
        private final AtomicInteger calls = new AtomicInteger();
        private volatile String lastKey;
        private volatile Duration lastTtl;
        private Mono<Boolean> reply;

        private StubRedisTemplate() {
            super((ReactiveRedisConnectionFactory) Proxy.newProxyInstance(
                    ReactiveRedisConnectionFactory.class.getClassLoader(),
                    new Class<?>[]{ReactiveRedisConnectionFactory.class},
                    (proxy, method, arguments) -> { throw new AssertionError("Unexpected Redis connection"); }));
        }

        @Override
        @SuppressWarnings("unchecked")
        public ReactiveValueOperations<String, String> opsForValue() {
            return (ReactiveValueOperations<String, String>) Proxy.newProxyInstance(
                    ReactiveValueOperations.class.getClassLoader(), new Class<?>[]{ReactiveValueOperations.class},
                    (proxy, method, arguments) -> {
                        assertEquals("setIfAbsent", method.getName());
                        assertEquals(3, arguments.length);
                        assertEquals("1", arguments[1]);
                        calls.incrementAndGet();
                        lastKey = (String) arguments[0];
                        lastTtl = (Duration) arguments[2];
                        String key = lastKey;
                        return reply != null ? reply : Mono.fromSupplier(() -> registered.add(key));
                    });
        }
    }
}
