package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.framework.core.error.BaseError;
import com.cloud.framework.core.error.ErrorCodeNamespace;
import com.cloud.framework.core.error.ErrorCodePrefix;
import com.cloud.framework.core.error.ErrorCodeRange;
import lombok.Getter;

/** Error codes owned by the persistence-nacos infrastructure module (EDGE 800 + local code). */
public enum PersistenceNacosError implements BaseError {
    INFRA_ACL_NACOS_PROVIDER_UNAVAILABLE(802, "Nacos配置服务不可用");

    @Getter private final int localCode;
    @Getter private final String message;

    PersistenceNacosError(int localCode, String message) {
        ErrorCodeRange.assertLocalCode(localCode);
        this.localCode = localCode;
        this.message = message;
    }

    @Override public ErrorCodePrefix getPrefix() { return new ErrorCodePrefix(ErrorCodeNamespace.EDGE, 0); }
}
