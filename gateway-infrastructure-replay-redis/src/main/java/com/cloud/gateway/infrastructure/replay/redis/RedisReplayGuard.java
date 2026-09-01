package com.cloud.gateway.infrastructure.replay.redis;

import com.cloud.gateway.application.security.ReplayGuard;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

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
        return redisTemplate.opsForValue()
                .setIfAbsent(keyResolver.evidence(evidenceKey), VALUE, ttl)
                .defaultIfEmpty(false)
                .toFuture();
    }
}
