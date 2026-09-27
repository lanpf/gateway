package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitQuota;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/** Synchronous Redis adapter; callers must move execution off the reactive event loop. */
@Slf4j
public final class RateLimitQuotaAdapter implements RateLimitQuota, AutoCloseable {
    private static final int WORKERS = 4;
    private static final int QUEUE_CAPACITY = 64;

    private final RedisTokenBucketLimiter limiter;
    private final RateLimitKeyResolver keyResolver;
    private final Duration acquireTimeout;
    private final ExecutorService workers;
    private final AtomicBoolean degraded = new AtomicBoolean();

    public RateLimitQuotaAdapter(RedisTokenBucketLimiter limiter, RateLimitKeyResolver keyResolver,
            Duration acquireTimeout) {
        this.limiter = Objects.requireNonNull(limiter);
        this.keyResolver = Objects.requireNonNull(keyResolver);
        this.acquireTimeout = Objects.requireNonNull(acquireTimeout);
        this.workers = new ThreadPoolExecutor(WORKERS, WORKERS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "gateway-quota-redis");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitRule rule, String key) {
        String resolvedKey = keyResolver.bucket(rule, key);
        try {
            RedisTokenBucketLimiter.BucketDecision decision = acquireWithinTimeout(rule, resolvedKey);
            if (degraded.compareAndSet(true, false)) {
                log.info("Rate limit Redis recovered: scene=quota");
            }
            return decision.allowed()
                    ? RateLimitDecision.allowed(rule.dimension(), key, decision.remaining())
                    : RateLimitDecision.rejected(rule.dimension(), key, decision.retryAfter());
        } catch (DataAccessException exception) {
            reportDegraded(exception.getClass().getSimpleName());
            return RateLimitDecision.unavailable(rule.dimension(), key);
        } catch (TimeoutException exception) {
            reportDegraded("timeout");
            return RateLimitDecision.unavailable(rule.dimension(), key);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return RateLimitDecision.unavailable(rule.dimension(), key);
        } catch (ExecutionException exception) {
            // Only dependency failures become "unavailable"; a programming error must stay visible.
            Throwable cause = exception.getCause();
            if (cause instanceof DataAccessException dataAccess) {
                reportDegraded(dataAccess.getClass().getSimpleName());
                return RateLimitDecision.unavailable(rule.dimension(), key);
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            reportDegraded(cause == null ? exception.getClass().getSimpleName() : cause.getClass().getSimpleName());
            return RateLimitDecision.unavailable(rule.dimension(), key);
        } catch (RuntimeException exception) {
            // A saturated worker pool rejects immediately; the dependency is effectively unavailable.
            reportDegraded(exception.getClass().getSimpleName());
            return RateLimitDecision.unavailable(rule.dimension(), key);
        }
    }

    /**
     * A hung Redis must not hold a request open indefinitely, so the blocking call is bounded and
     * cancelled. Cancellation interrupts the worker, which unblocks the client call.
     */
    private RedisTokenBucketLimiter.BucketDecision acquireWithinTimeout(RateLimitRule rule, String resolvedKey)
            throws TimeoutException, InterruptedException, ExecutionException {
        Future<RedisTokenBucketLimiter.BucketDecision> pending =
                workers.submit(() -> limiter.tryAcquire(rule, resolvedKey));
        try {
            return pending.get(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException exception) {
            pending.cancel(true);
            throw exception;
        }
    }

    private void reportDegraded(String failureType) {
        if (degraded.compareAndSet(false, true)) {
            // Redis exception text can contain keys; deliberately do not log its payload.
            log.warn("Rate limit Redis unavailable: scene=quota failureType={}", failureType);
        }
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }
}
