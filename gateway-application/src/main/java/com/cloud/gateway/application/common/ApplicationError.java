package com.cloud.gateway.application.common;

import com.cloud.framework.core.error.BaseError;
import com.cloud.framework.core.error.ErrorCodeNamespace;
import com.cloud.framework.core.error.ErrorCodePrefix;
import com.cloud.framework.core.error.ErrorCodeRange;
import lombok.Getter;

public enum ApplicationError implements BaseError {
    APP_POLICY_SNAPSHOT_VERSION_CONFLICT(600, "入口策略快照版本冲突"),
    APP_POLICY_SNAPSHOT_PUBLISH_CONFLICT(601, "入口策略快照发布冲突"),
    APP_CLIENT_CREDENTIAL_AUTHENTICATION_FAILED(602, "客户端凭据验证失败"),
    APP_SUBJECT_AUTHENTICATION_FAILED(603, "访问主体验证失败"),
    APP_POLICY_SNAPSHOT_HISTORY_NOT_FOUND(604, "入口策略历史快照不存在"),
    APP_POLICY_SNAPSHOT_NO_CHANGES(605, "入口策略定义没有变化"),
    APP_POLICY_DEFINITIONS_CHANGED(606, "入口策略定义在发布期间发生变化"),
    APP_POLICY_DEFINITION_UPDATE_CONFLICT(607, "入口策略定义更新冲突"),
    APP_POLICY_PUBLISH_LOCK_UNAVAILABLE(608, "入口策略发布锁获取失败"),
    APP_POLICY_ROLLBACK_LOCK_UNAVAILABLE(609, "入口策略回退锁获取失败"),
    APP_POLICY_ROLLBACK_IN_PROGRESS(610, "入口策略处于回退状态"),
    APP_POLICY_SNAPSHOT_PUBLISH_READ_ONLY(611, "入口策略快照发布只读"),
    APP_POLICY_DEFINITION_READ_ONLY(612, "入口策略定义只读"),
    APP_POLICY_OPERATION_TIMED_OUT(613, "入口策略操作超时，请检查当前状态后重试");

    @Getter private final int localCode;
    @Getter private final String message;

    ApplicationError(int localCode, String message) {
        ErrorCodeRange.assertLocalCode(localCode);
        this.localCode = localCode;
        this.message = message;
    }

    @Override public ErrorCodePrefix getPrefix() { return new ErrorCodePrefix(ErrorCodeNamespace.EDGE, 0); }
}
