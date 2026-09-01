package com.cloud.gateway.domain.common;

import com.cloud.framework.core.validation.Require;
import com.cloud.framework.domain.EntityId;

public abstract class StringEntityId extends EntityId<String> {
    protected StringEntityId(String value) {
        super(value);
    }

    @Override
    protected String validate(String value) {
        return Require.notBlank(value, DomainException::invalidEntityId).trim();
    }
}
