package com.cloud.gateway.infrastructure.ratelimit.redis;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** PREALLOCATED 模式：每实例批量从 Redis 取令牌入本地池，请求只打本地内存。 */
public class PreallocatedTokenPool {
    private static final DefaultRedisScript<List> BATCH_SCRIPT = new DefaultRedisScript<>();

    static {
        BATCH_SCRIPT.setLocation(new org.springframework.core.io.ClassPathResource("ratelimit_token_bucket.lua"));
        BATCH_SCRIPT.setResultType(List.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final long capacity;
    private final long refillPerSecond;
    private final int batch;
    private final AtomicLong local = new AtomicLong();
    private final AtomicLong refilledAt = new AtomicLong();

    public PreallocatedTokenPool(StringRedisTemplate redisTemplate, String keyPrefix,
                                 long capacity, long refillPerSecond, int batch) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.batch = batch;
        this.local.set(batch);
    }

    /** @return long[]{allowed(0/1), remaining(本地余量)} */
    public long[] tryAcquire(String key, int requested) {
        while (true) {
            long current = local.get();
            if (current >= requested) {
                if (local.compareAndSet(current, current - requested)) {
                    return new long[]{1, current - requested};
                }
                continue;
            }
            refillFromRedis(key);
            if (local.get() < requested) {
                return new long[]{0, local.get()};
            }
        }
    }

    private void refillFromRedis(String key) {
        long now = System.currentTimeMillis();
        long last = refilledAt.get();
        if (now - last < 500 || !refilledAt.compareAndSet(last, now)) {
            return;
        }
        List<?> result = redisTemplate.execute(
                BATCH_SCRIPT,
                List.of(keyPrefix + key),
                String.valueOf(capacity),
                String.valueOf(refillPerSecond),
                String.valueOf(batch));
        if (result != null && result.size() >= 2) {
            long allowed = (long) result.get(0);
            if (allowed == 1) {
                long taken = (long) result.get(1);
                local.updateAndGet(prev -> Math.max(prev, 0) + taken);
            }
        }
    }
}
