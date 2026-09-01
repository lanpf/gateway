package com.cloud.gateway.application.policy;

public interface PolicyRollbackState {
    boolean isActive();

    void activate();

    void clear();
}
