package com.cloud.gateway.infrastructure.persistence.nacos.operations;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@RequiredArgsConstructor
public final class DefaultNacosConfigOperations implements NacosConfigOperations {
    private final ConfigService configService;
    private final Map<Consumer<String>, Listener> listeners = new ConcurrentHashMap<>();

    @Override
    public String getConfig(String dataId, String group, long timeoutMillis) throws NacosException {
        return configService.getConfig(dataId, group, timeoutMillis);
    }

    @Override
    public String getConfigAndListen(
            String dataId,
            String group,
            long timeoutMillis,
            Consumer<String> changeCallback
    ) throws NacosException {
        Listener listener = listeners.computeIfAbsent(changeCallback, ignored -> listener(changeCallback));
        return configService.getConfigAndSignListener(dataId, group, timeoutMillis, listener);
    }

    @Override
    public boolean publishConfigCas(
            String dataId,
            String group,
            String content,
            String expectedMd5
    ) throws NacosException {
        return configService.publishConfigCas(dataId, group, content, expectedMd5, ConfigType.YAML.getType());
    }

    @Override
    public void removeListener(String dataId, String group, Consumer<String> changeCallback) {
        Listener listener = listeners.remove(changeCallback);
        if (listener != null) {
            configService.removeListener(dataId, group, listener);
        }
    }

    private static Listener listener(Consumer<String> changeCallback) {
        return new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                changeCallback.accept(configInfo);
            }
        };
    }
}
