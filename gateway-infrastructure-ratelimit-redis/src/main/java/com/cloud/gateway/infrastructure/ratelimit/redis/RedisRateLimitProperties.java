package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.framework.core.naming.Namespaced;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
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
}
