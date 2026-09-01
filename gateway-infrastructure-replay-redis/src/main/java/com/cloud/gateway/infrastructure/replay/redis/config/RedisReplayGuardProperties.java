package com.cloud.gateway.infrastructure.replay.redis.config;

import com.cloud.framework.core.naming.Namespaced;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(RedisReplayGuardProperties.PREFIX)
@Getter
@Setter
public class RedisReplayGuardProperties implements Namespaced {
    public static final String PREFIX = "gateway.security.replay";

    private String namespace;
}
