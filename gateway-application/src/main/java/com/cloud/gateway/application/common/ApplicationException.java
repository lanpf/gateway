package com.cloud.gateway.application.common;

import com.cloud.framework.core.error.BaseException;

public final class ApplicationException extends BaseException {
    public ApplicationException(ApplicationError error) { super(error); }
    public ApplicationException(ApplicationError error, Throwable cause) { super(error, cause); }
}
