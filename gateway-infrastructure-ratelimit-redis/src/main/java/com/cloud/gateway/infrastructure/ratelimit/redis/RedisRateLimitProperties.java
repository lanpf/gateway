package com.cloud.gateway.infrastructure.ratelimit.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.ratelimit.redis")
public class RedisRateLimitProperties {
    private String keyPrefix = "gateway:ratelimit:";
    private int preallocationBatch = 100;

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public int getPreallocationBatch() {
        return preallocationBatch;
    }

    public void setPreallocationBatch(int preallocationBatch) {
        this.preallocationBatch = preallocationBatch;
    }
}
