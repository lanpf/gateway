package com.cloud.gateway.application.configuration.publication;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import java.time.Duration;
import java.util.function.LongSupplier;
import lombok.RequiredArgsConstructor;
import lombok.Getter;

/** Stops starting further work after the budget; synchronous writes finish before lock release. */
public final class AccessOperationBudget {
    private final long timeoutNanos;
    private final LongSupplier ticker;

    public AccessOperationBudget(Duration timeout, LongSupplier ticker) {
        Require.allNotNull(timeout, ticker);
        Require.valid(timeout, value -> !value.isNegative() && !value.isZero());
        this.timeoutNanos = timeout.toNanos();
        this.ticker = ticker;
    }

    public Deadline start() { return new Deadline(ticker.getAsLong(), timeoutNanos, ticker); }

    @RequiredArgsConstructor
    public static final class Deadline {
        private final long started;
        private final long timeoutNanos;
        private final LongSupplier ticker;
        @Getter private int completedDefinitions;

        void definitionCompleted() { completedDefinitions++; }

        public void check() {
            if (Thread.currentThread().isInterrupted() || ticker.getAsLong() - started >= timeoutNanos) {
                throw new ApplicationException(ApplicationError.APP_POLICY_OPERATION_TIMED_OUT);
            }
        }
    }
}
