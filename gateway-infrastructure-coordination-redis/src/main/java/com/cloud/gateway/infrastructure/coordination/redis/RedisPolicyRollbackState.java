package com.cloud.gateway.infrastructure.coordination.redis;

import com.cloud.gateway.application.policy.PolicyRollbackState;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

@RequiredArgsConstructor
public final class RedisPolicyRollbackState implements PolicyRollbackState {
    private static final String VALUE = "1";
    private final StringRedisTemplate redisTemplate;
    private final PolicyRollbackStateKeyResolver keyResolver;

    @Override
    public boolean isActive() {
        try {
            return redisTemplate.hasKey(keyResolver.rollbackState());
        } catch (InfrastructureException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void activate() {
        try {
            Boolean created = redisTemplate.opsForValue().setIfAbsent(keyResolver.rollbackState(), VALUE);
            if (created == null) {
                throw unavailable();
            }
        } catch (InfrastructureException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void clear() {
        try {
            Boolean deleted = redisTemplate.delete(keyResolver.rollbackState());
            if (!deleted && isActive()) {
                throw unavailable();
            }
        } catch (InfrastructureException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private static InfrastructureException unavailable() {
        return new InfrastructureException(InfrastructureError.INFRA_TECH_POLICY_ROLLBACK_STATE_FAILED);
    }

    private static InfrastructureException unavailable(Throwable cause) {
        return new InfrastructureException(InfrastructureError.INFRA_TECH_POLICY_ROLLBACK_STATE_FAILED, cause);
    }
}
