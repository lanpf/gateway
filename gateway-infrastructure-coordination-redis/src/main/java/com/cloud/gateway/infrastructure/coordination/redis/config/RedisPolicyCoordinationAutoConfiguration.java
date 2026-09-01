package com.cloud.gateway.infrastructure.coordination.redis.config;

import com.cloud.framework.core.naming.NamespaceResolver;
import com.cloud.framework.core.naming.NamespacedResourceNameResolver;
import com.cloud.gateway.application.policy.PolicyRollbackState;
import com.cloud.gateway.infrastructure.coordination.redis.PolicyRollbackStateKeyResolver;
import com.cloud.gateway.infrastructure.coordination.redis.RedisPolicyRollbackState;
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
    PolicyRollbackStateKeyResolver policyRollbackStateKeyResolver(
            RedisPolicyCoordinationProperties properties,
            NamespaceResolver namespaceResolver
    ) {
        return new PolicyRollbackStateKeyResolver(
                new NamespacedResourceNameResolver(namespaceResolver, properties));
    }

    @Bean
    PolicyRollbackState policyRollbackState(
            StringRedisTemplate redisTemplate,
            PolicyRollbackStateKeyResolver keyResolver
    ) {
        return new RedisPolicyRollbackState(redisTemplate, keyResolver);
    }
}
