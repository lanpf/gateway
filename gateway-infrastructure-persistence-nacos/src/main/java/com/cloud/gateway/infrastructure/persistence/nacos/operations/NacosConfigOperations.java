package com.cloud.gateway.infrastructure.persistence.nacos.operations;

import com.alibaba.nacos.api.exception.NacosException;
import java.util.function.Consumer;

public interface NacosConfigOperations {
    String getConfig(String dataId, String group, long timeoutMillis) throws NacosException;

    String getConfigAndListen(
            String dataId,
            String group,
            long timeoutMillis,
            Consumer<String> changeCallback
    ) throws NacosException;

    boolean publishConfigCas(
            String dataId,
            String group,
            String content,
            String expectedMd5
    ) throws NacosException;

    void removeListener(String dataId, String group, Consumer<String> changeCallback);
}
