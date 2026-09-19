package com.cloud.gateway.domain.ingress.access.subject;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public record SessionId(String value) {
    public SessionId {
        value = Require.notBlank(value, DomainException::invalidField).trim();
    }
}
