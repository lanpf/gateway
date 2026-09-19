package com.cloud.gateway.application.configuration.publication;

public interface AccessRollbackState {
    boolean isActive();

    void activate();

    void clear();
}
