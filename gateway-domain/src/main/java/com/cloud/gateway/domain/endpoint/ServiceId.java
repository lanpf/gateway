package com.cloud.gateway.domain.endpoint;


import com.cloud.framework.core.validation.Require;

public record ServiceId(String value) {
    public ServiceId {
        value = Require.notBlank(value).trim();
    }
}
