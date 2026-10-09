package com.cloud.gateway.infrastructure.persistence.nacos.history.payload;

import com.cloud.framework.core.validation.Require;

/** Fixed Nacos login contract; encoded as a form only at the HTTP adapter boundary. */
public record NacosLoginRequestPayload(String username, String password) {
    public NacosLoginRequestPayload {
        Require.allNotNull(username, password);
    }
}
