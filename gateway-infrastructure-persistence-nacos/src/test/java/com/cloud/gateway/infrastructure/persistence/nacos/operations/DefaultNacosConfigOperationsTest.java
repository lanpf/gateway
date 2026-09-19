package com.cloud.gateway.infrastructure.persistence.nacos.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class DefaultNacosConfigOperationsTest {

    @Test
    void shouldForwardPushedContentWithoutReadingConfigAgain() throws Exception {
        AtomicReference<Listener> registeredListener = new AtomicReference<>();
        AtomicReference<Listener> removedListener = new AtomicReference<>();
        ConfigService configService = configService(registeredListener, removedListener);
        DefaultNacosConfigOperations operations = new DefaultNacosConfigOperations(configService);
        AtomicReference<String> observedContent = new AtomicReference<>();
        Consumer<String> callback = observedContent::set;

        String initialContent = operations.getConfigAndListen("access.yaml", "GATEWAY", 1_000, callback);
        registeredListener.get().receiveConfigInfo("version: 2");
        operations.removeListener("access.yaml", "GATEWAY", callback);

        assertEquals("version: 1", initialContent);
        assertEquals("version: 2", observedContent.get());
        assertSame(registeredListener.get(), removedListener.get());
    }

    private static ConfigService configService(
            AtomicReference<Listener> registeredListener,
            AtomicReference<Listener> removedListener
    ) {
        return (ConfigService) Proxy.newProxyInstance(
                ConfigService.class.getClassLoader(),
                new Class<?>[]{ConfigService.class},
                (proxy, method, arguments) -> {
                    if ("getConfigAndSignListener".equals(method.getName())) {
                        registeredListener.set((Listener) arguments[3]);
                        return "version: 1";
                    }
                    if ("removeListener".equals(method.getName())) {
                        removedListener.set((Listener) arguments[2]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
