package com.cloud.gateway.domain.ingress.access.subject;

import com.cloud.gateway.domain.common.DomainException;

import com.cloud.framework.core.validation.Require;

public record SessionId(String value) {
    public SessionId {
        value = Require.notBlank(value, DomainException::invalidObjectField).trim();
    }
}
