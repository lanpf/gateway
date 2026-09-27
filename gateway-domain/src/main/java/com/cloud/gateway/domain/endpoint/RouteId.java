package com.cloud.gateway.domain.endpoint;

import com.cloud.gateway.domain.common.DomainException;

import com.cloud.framework.core.validation.Require;

public record RouteId(String value) {
    public RouteId {
        value = Require.notBlank(value, DomainException::invalidObjectField).trim();
    }
}
