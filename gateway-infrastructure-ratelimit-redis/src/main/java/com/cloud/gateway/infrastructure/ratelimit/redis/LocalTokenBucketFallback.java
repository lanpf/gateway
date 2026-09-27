package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.springframework.util.Assert;

/** Bounded per-instance emergency quotas, not a cluster-wide EXACT guarantee. */
final class LocalTokenBucketFallback {
    private static final double NANOS_PER_SECOND = 1_000_000_000d;
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final int maximumBuckets;
    private final LongSupplier ticker;

    LocalTokenBucketFallback(int maximumBuckets, LongSupplier ticker) {
        Assert.isTrue(maximumBuckets > 0, "maximumBuckets must be positive");
        this.maximumBuckets = maximumBuckets;
        this.ticker = Objects.requireNonNull(ticker);
    }

    synchronized RateLimitDecision tryAcquire(RateLimitRule rule, String key, String resolvedKey) {
        long now = ticker.getAsLong();
        Bucket bucket = buckets.get(resolvedKey);
        if (bucket == null) {
            if (buckets.size() >= maximumBuckets) {
                // Never evict a depleted bucket to mint fresh quota for an active identity.
                buckets.entrySet().removeIf(entry -> entry.getValue().available(now) >= entry.getValue().capacity);
            }
            if (buckets.size() >= maximumBuckets) {
                return RateLimitDecision.unavailable(rule.dimension(), key);
            }
            bucket = new Bucket(rule.capacity(), now, rule.capacity(), rule.refillPerSecond());
        }
        double tokens = Math.min(rule.capacity(), bucket.available(now));
        boolean allowed = tokens >= 1;
        if (allowed) {
            tokens -= 1;
        }
        buckets.put(resolvedKey, new Bucket(tokens, now, rule.capacity(), rule.refillPerSecond()));
        if (allowed) {
            return RateLimitDecision.allowed(rule.dimension(), key, (long) tokens);
        }
        long retryMillis = Math.max(1, (long) Math.ceil((1 - tokens) / rule.refillPerSecond() * 1000));
        return RateLimitDecision.rejected(rule.dimension(), key, Duration.ofMillis(retryMillis));
    }

    private record Bucket(double tokens, long timestamp, int capacity, int refill) {
        double available(long now) {
            return Math.min(capacity, tokens + Math.max(0, now - timestamp) / NANOS_PER_SECOND * refill);
        }
    }
}
