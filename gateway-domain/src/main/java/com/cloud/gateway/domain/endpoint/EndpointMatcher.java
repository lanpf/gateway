package com.cloud.gateway.domain.endpoint;

import com.cloud.gateway.domain.common.DomainException;

import com.cloud.framework.core.validation.Require;

public record EndpointMatcher(String httpMethod, String pathPattern) {
    public EndpointMatcher {
        httpMethod = Require.notBlank(httpMethod, DomainException::invalidObjectField).trim().toUpperCase();
        pathPattern = Require.notBlank(pathPattern, DomainException::invalidObjectField).trim();
    }
}
