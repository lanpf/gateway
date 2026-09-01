package com.cloud.gateway.infrastructure.replay.redis.config;

import com.cloud.framework.core.naming.NamespaceResolver;
import com.cloud.framework.core.naming.NamespacedResourceNameResolver;
import com.cloud.gateway.application.security.ReplayGuard;
import java.time.Clock;

import com.cloud.gateway.infrastructure.replay.redis.ReplayGuardKeyResolver;
import com.cloud.gateway.infrastructure.replay.redis.RedisReplayGuard;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@AutoConfiguration
@EnableConfigurationProperties(RedisReplayGuardProperties.class)
public class RedisReplayGuardAutoConfiguration {
    @Bean
    ReplayGuardKeyResolver replayGuardKeyResolver(
            RedisReplayGuardProperties properties,
            NamespaceResolver namespaceResolver
    ) {
        return new ReplayGuardKeyResolver(
                new NamespacedResourceNameResolver(namespaceResolver, properties));
    }

    @Bean
    ReplayGuard redisReplayGuard(
            ReactiveStringRedisTemplate redisTemplate,
            ReplayGuardKeyResolver keyResolver,
            Clock clock) {
        return new RedisReplayGuard(redisTemplate, keyResolver, clock);
    }
}
