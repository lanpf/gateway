package com.cloud.gateway.domain.endpoint;


import com.cloud.framework.core.validation.Require;

public record EndpointMatcher(String httpMethod, String pathPattern) {
    public EndpointMatcher {
        httpMethod = Require.notBlank(httpMethod).trim().toUpperCase();
        pathPattern = Require.notBlank(pathPattern).trim();
    }
}
