package com.cloud.gateway.infrastructure.coordination.redis;

import com.cloud.framework.core.error.BaseError;
import com.cloud.framework.core.error.ErrorCodeNamespace;
import com.cloud.framework.core.error.ErrorCodePrefix;
import com.cloud.framework.core.error.ErrorCodeRange;
import lombok.Getter;

/** Error codes owned by the coordination-redis infrastructure module (EDGE 800 + local code). */
public enum CoordinationRedisError implements BaseError {
    INFRA_TECH_POLICY_ROLLBACK_STATE_FAILED(705, "入口策略回退状态处理失败");

    @Getter private final int localCode;
    @Getter private final String message;

    CoordinationRedisError(int localCode, String message) {
        ErrorCodeRange.assertLocalCode(localCode);
        this.localCode = localCode;
        this.message = message;
    }

    @Override public ErrorCodePrefix getPrefix() { return new ErrorCodePrefix(ErrorCodeNamespace.EDGE, 0); }
}
