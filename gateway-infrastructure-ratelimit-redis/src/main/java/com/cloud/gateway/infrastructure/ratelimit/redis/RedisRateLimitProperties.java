package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.framework.core.naming.Namespaced;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(RedisRateLimitProperties.PREFIX)
public class RedisRateLimitProperties implements Namespaced {
    public static final String PREFIX = "gateway.ratelimit.redis";

    private String namespace;
    @Min(1)
    private int localMaxBuckets = 10000;

    /**
     * Upper bound on one Redis quota call. A hanging dependency must not hold a request open; on
     * expiry the denied is {@code unavailable}, so the configured RedisFailureMode decides.
     */
    @NotNull @DurationMin(millis = 1)
    private Duration acquireTimeout = Duration.ofMillis(500);
}
