package com.cloud.gateway.infrastructure.coordination.redis.config;

import com.cloud.framework.core.naming.NamespaceResolver;
import com.cloud.framework.starter.lock.redis.RedisLockProperties;
import com.cloud.gateway.infrastructure.policy.config.AccessOperationProperties;
import com.cloud.framework.core.error.FrameworkError;
import com.cloud.framework.core.error.FrameworkException;
import com.cloud.framework.core.naming.NamespacedResourceNameResolver;
import com.cloud.gateway.application.configuration.publication.AccessRollbackState;
import com.cloud.gateway.infrastructure.coordination.redis.AccessRollbackStateKeyResolver;
import com.cloud.gateway.infrastructure.coordination.redis.RedisAccessRollbackState;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
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
    com.cloud.gateway.application.configuration.publication.AccessRollbackState policyRollbackState(
            StringRedisTemplate redisTemplate,
            AccessRollbackStateKeyResolver keyResolver,
            RedisLockProperties lockProperties,
            AccessOperationProperties operationProperties,
            RedisProperties redisProperties
    ) {
        if (lockProperties.getLeaseTime().compareTo(operationProperties.getTimeout()
                .plus(operationProperties.getMaxBlockingTime()).plus(operationProperties.getLeaseSafetyMargin())) < 0) {
            throw new FrameworkException(FrameworkError.OBJECT_INVALID,
                    "Access lock lease must cover the operation budget, maximum blocking time and safety margin");
        }
        validateBlockingLimits(redisProperties, operationProperties);
        return new RedisAccessRollbackState(redisTemplate, keyResolver);
    }
    static void validateBlockingLimits(RedisProperties redis, AccessOperationProperties operations) {
        var commandTimeout = redis.getTimeout();
        var connectTimeout = redis.getConnectTimeout();
        if (commandTimeout == null || connectTimeout == null
                || commandTimeout.isNegative() || commandTimeout.isZero()
                || connectTimeout.isNegative() || connectTimeout.isZero()
                || commandTimeout.plus(connectTimeout).compareTo(operations.getMaxBlockingTime()) > 0) {
            throw new FrameworkException(FrameworkError.OBJECT_INVALID,
                    "Redis command and connection timeouts must be positive and fit the maximum blocking time");
        }
    }
}
