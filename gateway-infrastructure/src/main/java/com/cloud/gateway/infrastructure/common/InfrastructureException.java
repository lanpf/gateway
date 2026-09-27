package com.cloud.gateway.infrastructure.common;

import com.cloud.framework.core.error.BaseError;
import com.cloud.framework.core.error.BaseException;

public final class InfrastructureException extends BaseException {
    public InfrastructureException(InfrastructureError error) { super(error); }
    public InfrastructureException(InfrastructureError error, Throwable cause) { super(error, cause); }

    /** Module-owned infrastructure error enums (security-spring/coordination-redis/persistence-nacos). */
    public InfrastructureException(BaseError error) { super(error); }
    public InfrastructureException(BaseError error, Throwable cause) { super(error, cause); }
}
