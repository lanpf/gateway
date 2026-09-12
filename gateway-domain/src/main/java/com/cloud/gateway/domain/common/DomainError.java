package com.cloud.gateway.domain.common;

import com.cloud.framework.core.error.BaseError;
import com.cloud.framework.core.error.ErrorCodeNamespace;
import com.cloud.framework.core.error.ErrorCodePrefix;
import com.cloud.framework.core.error.ErrorCodeRange;
import lombok.Getter;

public enum DomainError implements BaseError {
    DOMAIN_ENTITY_ID_INVALID(0, "领域实体ID非法"),
    DOMAIN_OBJECT_FIELD_REQUIRED(1, "领域对象字段不能为空"),
    DOMAIN_OBJECT_FIELD_INVALID(2, "领域对象字段非法"),
    DOMAIN_OBJECT_STATE_INVALID(3, "领域对象状态异常"),

    ACCESS_SNAPSHOT_NOT_FOUND(100, "入口策略快照不存在"),
    ACCESS_SNAPSHOT_ALREADY_EXISTS(101, "入口策略快照已存在"),
    ACCESS_SNAPSHOT_INVALID(102, "入口策略快照非法"),

    ENDPOINT_NOT_FOUND(150, "端点目录不存在"),
    ENDPOINT_ALREADY_EXISTS(151, "端点目录已存在"),
    ENDPOINT_AMBIGUOUS(152, "端点定义存在冲突"),

    APP_NOT_FOUND(200, "应用不存在"),
    APP_ALREADY_EXISTS(201, "应用已存在"),
    APP_AMBIGUOUS(202, "应用定义存在冲突"),

    CLIENT_NOT_FOUND(250, "调用方不存在"),
    CLIENT_ALREADY_EXISTS(251, "调用方已存在"),
    CLIENT_AMBIGUOUS(252, "调用方定义存在冲突");

    @Getter private final int localCode;
    @Getter private final String message;

    DomainError(int localCode, String message) {
        ErrorCodeRange.assertLocalCode(localCode);
        this.localCode = localCode;
        this.message = message;
    }

    @Override public ErrorCodePrefix getPrefix() { return new ErrorCodePrefix(ErrorCodeNamespace.EDGE, 0); }
}
