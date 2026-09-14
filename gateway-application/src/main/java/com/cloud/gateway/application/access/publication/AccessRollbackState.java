package com.cloud.gateway.application.access.publication;

public interface AccessRollbackState {
    boolean isActive();

    void activate();

    void clear();
}
