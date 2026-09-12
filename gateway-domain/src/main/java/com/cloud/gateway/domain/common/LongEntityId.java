package com.cloud.gateway.domain.common;

import com.cloud.framework.core.validation.Require;
import com.cloud.framework.domain.EntityId;

public abstract class LongEntityId extends EntityId<Long> {
    protected LongEntityId(Long value) {
        super(value);
    }

    @Override
    protected Long normalize(Long value) {
        return Require.positive(value, DomainException::invalidEntityId);
    }
}
