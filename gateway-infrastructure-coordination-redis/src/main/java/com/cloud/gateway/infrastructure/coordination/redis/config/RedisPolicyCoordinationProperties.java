package com.cloud.gateway.infrastructure.coordination.redis.config;

import com.cloud.framework.core.naming.Namespaced;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(RedisPolicyCoordinationProperties.PREFIX)
public class RedisPolicyCoordinationProperties implements Namespaced {
    public static final String PREFIX = "gateway.coordination.redis";

    private String namespace;
}
