package com.cloud.gateway.infrastructure.common;

import com.cloud.framework.core.error.BaseError;
import com.cloud.framework.core.error.ErrorCodeNamespace;
import com.cloud.framework.core.error.ErrorCodePrefix;
import com.cloud.framework.core.error.ErrorCodeRange;
import lombok.Getter;

public enum InfrastructureError implements BaseError {
    INFRA_TECH_POLICY_SERIALIZATION_FAILED(700, "入口策略序列化失败"),
    INFRA_TECH_POLICY_STORE_UNAVAILABLE(701, "入口策略存储不可用"),
    INFRA_TECH_SECRET_NOT_FOUND(702, "渠道密钥不存在"),
    INFRA_TECH_POLICY_DESERIALIZATION_FAILED(703, "入口策略反序列化失败"),
    INFRA_TECH_POLICY_LAST_KNOWN_GOOD_FAILED(704, "入口策略最后有效快照处理失败"),
    INFRA_TECH_POLICY_ROLLBACK_STATE_FAILED(705, "入口策略回退状态处理失败"),

    INFRA_ACL_ACCESS_TOKEN_PROVIDER_UNAVAILABLE(800, "Access Token验证服务不可用"),
    INFRA_ACL_BROWSER_SESSION_PROVIDER_UNAVAILABLE(801, "浏览器会话验证服务不可用"),
    INFRA_ACL_NACOS_PROVIDER_UNAVAILABLE(802, "Nacos配置服务不可用"),
    INFRA_ACL_NACOS_CONTENT_INVALID(803, "Nacos入口策略内容非法");

    @Getter private final int localCode;
    @Getter private final String message;

    InfrastructureError(int localCode, String message) {
        ErrorCodeRange.assertLocalCode(localCode);
        this.localCode = localCode;
        this.message = message;
    }

    @Override public ErrorCodePrefix getPrefix() { return new ErrorCodePrefix(ErrorCodeNamespace.EDGE, 0); }
}
