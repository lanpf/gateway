package com.cloud.gateway.application.access.publication;

import com.cloud.framework.lock.LockContext;
import com.cloud.framework.lock.LockExecutor;
import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class AccessRollbackLock {
    private static final String RESOURCE_KEY = "ingress-policy";

    private final LockExecutor lockExecutor;

    public <T> T execute(Callable<T> action) {
        LockContext context = new LockContext("rollback", RESOURCE_KEY);
        try {
            return lockExecutor.execute(context, action).orElseThrow(
                    () -> new ApplicationException(ApplicationError.APP_POLICY_ROLLBACK_LOCK_UNAVAILABLE));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApplicationException(ApplicationError.APP_POLICY_ROLLBACK_LOCK_UNAVAILABLE, exception);
        }
    }
}
