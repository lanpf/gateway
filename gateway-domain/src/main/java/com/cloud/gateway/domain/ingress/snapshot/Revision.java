package com.cloud.gateway.domain.ingress.snapshot;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public record Revision(String value) {
    public Revision {
        value = Require.notBlank(value, DomainException::invalidField).trim();
    }
}
