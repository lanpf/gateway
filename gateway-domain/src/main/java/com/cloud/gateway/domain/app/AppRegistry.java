package com.cloud.gateway.domain.app;

import com.cloud.gateway.domain.common.AbstractAggregateRootRegistry;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;

import java.util.Collection;

public final class AppRegistry extends AbstractAggregateRootRegistry<App, AppId> {

    public AppRegistry(Collection<App> apps) {
        super(apps);
    }

    @Override
    protected DomainException ambiguous() {
        return new DomainException(DomainError.APP_AMBIGUOUS);
    }

}
