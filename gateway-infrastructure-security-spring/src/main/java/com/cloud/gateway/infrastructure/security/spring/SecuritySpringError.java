package com.cloud.gateway.infrastructure.security.spring;

import com.cloud.framework.core.error.BaseError;
import com.cloud.framework.core.error.ErrorCodeNamespace;
import com.cloud.framework.core.error.ErrorCodePrefix;
import com.cloud.framework.core.error.ErrorCodeRange;
import lombok.Getter;

/** Error codes owned by the security-spring infrastructure module (EDGE 800 + local code). */
public enum SecuritySpringError implements BaseError {
    INFRA_TECH_SECRET_NOT_FOUND(702, "客户端密钥不存在");

    @Getter private final int localCode;
    @Getter private final String message;

    SecuritySpringError(int localCode, String message) {
        ErrorCodeRange.assertLocalCode(localCode);
        this.localCode = localCode;
        this.message = message;
    }

    @Override public ErrorCodePrefix getPrefix() { return new ErrorCodePrefix(ErrorCodeNamespace.EDGE, 0); }
}
