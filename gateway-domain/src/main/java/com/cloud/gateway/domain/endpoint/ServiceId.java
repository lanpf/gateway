package com.cloud.gateway.domain.endpoint;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public record ServiceId(String value) {
    public ServiceId {
        value = Require.notBlank(value, DomainException::invalidField).trim();
    }
}
