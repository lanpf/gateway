package com.cloud.gateway.infrastructure.coordination.redis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloud.gateway.infrastructure.common.InfrastructureException;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisAccessRollbackStateTest {
    private static final String KEY = "policy:rollback-state";
    private static final AccessRollbackStateKeyResolver KEY_RESOLVER =
            new AccessRollbackStateKeyResolver(names -> String.join(":", names));

    @Test
    void shouldActivatePersistentStateWithoutExpiry() {
        RecordingRedisTemplate template = new RecordingRedisTemplate();
        template.setIfAbsentResult = true;

        new RedisAccessRollbackState(template, KEY_RESOLVER).activate();

        assertEquals(KEY, template.writtenKey);
        assertEquals("1", template.writtenValue);
    }

    @Test
    void shouldTreatExistingStateAsIdempotentActivation() {
        RecordingRedisTemplate template = new RecordingRedisTemplate();
        template.setIfAbsentResult = false;

        assertDoesNotThrow(() -> new RedisAccessRollbackState(template, KEY_RESOLVER).activate());
    }

    @Test
    void shouldFailClearWhenStateStillExists() {
        RecordingRedisTemplate template = new RecordingRedisTemplate();
        template.deleteResult = false;
        template.hasKeyResult = true;

        assertThrows(InfrastructureException.class,
                () -> new RedisAccessRollbackState(template, KEY_RESOLVER).clear());
    }

    private static final class RecordingRedisTemplate extends StringRedisTemplate {
        private Boolean setIfAbsentResult;
        private Boolean deleteResult;
        private Boolean hasKeyResult;
        private String writtenKey;
        private String writtenValue;

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, arguments) -> {
                        if ("setIfAbsent".equals(method.getName()) && arguments.length == 2) {
                            writtenKey = (String) arguments[0];
                            writtenValue = (String) arguments[1];
                            return setIfAbsentResult;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Override public Boolean delete(String key) { return deleteResult; }
        @Override public Boolean hasKey(String key) { return hasKeyResult; }
    }
}
