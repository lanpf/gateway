package com.cloud.gateway.domain.endpoint;


import com.cloud.framework.core.validation.Require;

public record RouteId(String value) {
    public RouteId {
        value = Require.notBlank(value).trim();
    }
}
