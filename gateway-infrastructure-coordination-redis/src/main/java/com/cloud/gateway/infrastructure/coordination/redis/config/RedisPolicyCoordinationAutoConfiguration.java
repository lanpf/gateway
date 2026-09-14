package com.cloud.gateway.infrastructure.coordination.redis.config;

import com.cloud.framework.core.naming.NamespaceResolver;
import com.cloud.framework.core.naming.NamespacedResourceNameResolver;
import com.cloud.gateway.application.access.publication.AccessRollbackState;
import com.cloud.gateway.infrastructure.coordination.redis.AccessRollbackStateKeyResolver;
import com.cloud.gateway.infrastructure.coordination.redis.RedisAccessRollbackState;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@AutoConfigureBefore(name = "com.cloud.gateway.infrastructure.policy.config.PolicyInfrastructureAutoConfiguration")
@EnableConfigurationProperties(RedisPolicyCoordinationProperties.class)
public class RedisPolicyCoordinationAutoConfiguration {

    @Bean
    AccessRollbackStateKeyResolver policyRollbackStateKeyResolver(
            RedisPolicyCoordinationProperties properties,
            NamespaceResolver namespaceResolver
    ) {
        return new AccessRollbackStateKeyResolver(
                new NamespacedResourceNameResolver(namespaceResolver, properties));
    }

    @Bean
    com.cloud.gateway.application.access.publication.AccessRollbackState policyRollbackState(
            StringRedisTemplate redisTemplate,
            AccessRollbackStateKeyResolver keyResolver
    ) {
        return new RedisAccessRollbackState(redisTemplate, keyResolver);
    }
}
