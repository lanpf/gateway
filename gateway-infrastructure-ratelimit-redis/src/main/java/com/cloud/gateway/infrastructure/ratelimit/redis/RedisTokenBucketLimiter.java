package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Atomic single-bucket EXACT quota; script and its response protocol are versioned with this adapter. */
public final class RedisTokenBucketLimiter {
    private static final String SCRIPT_RESOURCE = "ratelimit_token_bucket.lua";
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>();

    static {
        SCRIPT.setLocation(new ClassPathResource(SCRIPT_RESOURCE));
        SCRIPT.setResultType(List.class);
    }
    private final StringRedisTemplate redisTemplate;

    public RedisTokenBucketLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Require.notNull(redisTemplate);
    }

    BucketDecision tryAcquire(RateLimitRule rule, String resolvedKey) {
        List<?> values = redisTemplate.execute(SCRIPT, List.of(resolvedKey),
                Integer.toString(rule.capacity()), Integer.toString(rule.refillPerSecond()));
        if (values == null || values.size() != 3
                || !(values.get(0) instanceof Long allowed)
                || !(values.get(1) instanceof Long remaining)
                || !(values.get(2) instanceof Long retryMillis)
                || (allowed != 0 && allowed != 1) || remaining < 0 || remaining > rule.capacity()
                || retryMillis < 0 || (allowed == 0 && retryMillis == 0)
                || (allowed == 1 && retryMillis != 0)) {
            throw new InvalidDataAccessResourceUsageException("Invalid rate limit script response");
        }
        return new BucketDecision(allowed == 1, remaining, Duration.ofMillis(retryMillis));
    }

    record BucketDecision(boolean allowed, long remaining, Duration retryAfter) { }
}
