package com.cloud.gateway.infrastructure.replay.redis;

import com.cloud.gateway.application.security.ReplayGuard;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public final class RedisReplayGuard implements ReplayGuard {
    private static final String VALUE = "1";
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ReplayGuardKeyResolver keyResolver;
    private final Clock clock;

    @Override
    public CompletionStage<Boolean> register(String evidenceKey, Instant expiresAt) {
        Duration ttl = Duration.between(clock.instant(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return CompletableFuture.completedFuture(false);
        }
        // Redis expiration precision is milliseconds: round up, never truncate
        // an accepted sub-millisecond remainder to zero or expire it early.
        long ttlMillis = ttl.toMillis();
        if (!ttl.equals(Duration.ofMillis(ttlMillis))) {
            ttlMillis = Math.addExact(ttlMillis, 1);
        }
        return redisTemplate.opsForValue()
                .setIfAbsent(keyResolver.evidence(evidenceKey), VALUE, Duration.ofMillis(ttlMillis))
                .switchIfEmpty(Mono.error(new InvalidDataAccessResourceUsageException("Missing replay registration response")))
                .toFuture();
    }
}
