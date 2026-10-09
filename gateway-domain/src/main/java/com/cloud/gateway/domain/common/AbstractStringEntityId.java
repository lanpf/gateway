package com.cloud.gateway.domain.common;

import com.cloud.framework.core.validation.Require;
import com.cloud.framework.domain.EntityId;

public abstract class AbstractStringEntityId extends EntityId<String> {
    protected AbstractStringEntityId(String value) {
        super(value);
    }

    @Override
    protected String normalize(String value) {
        return Require.notBlank(value, DomainException::invalidEntityId).trim();
    }
}
