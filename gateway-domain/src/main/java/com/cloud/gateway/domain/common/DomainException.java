package com.cloud.gateway.domain.common;

import com.cloud.framework.core.error.BaseException;

public final class DomainException extends BaseException {
    public DomainException(DomainError error) {
        super(error);
    }

    public static DomainException invalidEntityId() {
        return new DomainException(DomainError.DOMAIN_ENTITY_ID_INVALID);
    }

    public static DomainException missingField() {
        return new DomainException(DomainError.DOMAIN_OBJECT_REQUIRED);
    }

    public static DomainException invalidObjectField() {
        return new DomainException(DomainError.DOMAIN_OBJECT_INVALID);
    }

    public static DomainException invalidState() {
        return new DomainException(DomainError.DOMAIN_OBJECT_abnormal);
    }
}
