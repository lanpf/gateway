package com.cloud.gateway.domain.endpoint;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public record EndpointMatcher(String httpMethod, String pathPattern) {
    public EndpointMatcher {
        httpMethod = Require.notBlank(httpMethod, DomainException::missingField).trim().toUpperCase();
        pathPattern = Require.notBlank(pathPattern, DomainException::missingField).trim();
    }
}
